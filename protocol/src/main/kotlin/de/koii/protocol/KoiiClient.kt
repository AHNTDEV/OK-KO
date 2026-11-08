package de.koii.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Client for the TE SysEx file protocol. One request is in flight at a time.
 * Everything beyond GREET/INIT/LIST/GET/PUT/DELETE of own uploads is unverified on hardware; see README.
 */
class KoiiClient(
    private val transport: MidiTransport,
    scope: CoroutineScope,
    private val requestTimeoutMs: Long = 4_000,
) : AutoCloseable {

    private val lock = Mutex()
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TeFrame>>()
    @Volatile private var identityWaiter: CompletableDeferred<IdentityReply>? = null
    private var nextId = Random.nextInt(4096)
    @Volatile private var identityCode = 0

    var chunkSize: Int = 0
        private set

    private val _events = MutableSharedFlow<FileEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<FileEvent> = _events.asSharedFlow()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    /** Device debug logs (frames with marker 0x33). */
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private val dirCache = ConcurrentHashMap<String, Int>()

    private val collector: Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        transport.incoming.collect { handle(it) }
    }

    private fun handle(msg: ByteArray) {
        identityWaiter?.let { w ->
            Identity.parseReply(msg)?.let { w.complete(it); return }
        }
        val frame = Frame.parse(msg) ?: return
        when (frame.kind) {
            FrameKind.DEVICE_LOG -> _logs.tryEmit(String(frame.payload, Charsets.UTF_8).trim('\u0000', ' ', '\n'))
            FrameKind.RESPONSE ->
                if (frame.hasRequestId) pending.remove(frame.requestId)?.complete(frame)
                else if (frame.command == Te.CMD_FILE) FileMsg.parseEvent(frame.payload)?.let(::publish)
            FrameKind.REQUEST -> Unit
        }
    }

    private fun publish(e: FileEvent) {
        if (e is FileEvent.Deleted || e is FileEvent.Moved) dirCache.clear()
        _events.tryEmit(e)
    }

    // ---- session -------------------------------------------------------------------------

    suspend fun connect(subscribe: Boolean = true): DeviceInfo {
        val identity = identify()
        identityCode = identity.deviceId
        val greet = greet()
        val chunk = initFiles(subscribe)
        return DeviceInfo(identity, greet, chunk)
    }

    suspend fun identify(): IdentityReply {
        val waiter = CompletableDeferred<IdentityReply>()
        identityWaiter = waiter
        try {
            transport.send(Identity.REQUEST)
            return withTimeoutOrNull(requestTimeoutMs) { waiter.await() }
                ?: throw KoiiTimeoutException("No identity reply from device")
        } finally {
            identityWaiter = null
        }
    }

    suspend fun greet(): GreetInfo {
        val f = call(Te.CMD_GREET, ByteArray(0))
        return GreetInfo.parse(String(f.payload, Charsets.UTF_8).trim('\u0000'))
    }

    private suspend fun initFiles(subscribe: Boolean): Int {
        val f = file(FileMsg.init(subscribe))
        chunkSize = FileMsg.parseInit(f.payload)
        return chunkSize
    }

    // ---- low level -----------------------------------------------------------------------

    private suspend fun call(command: Int, payload: ByteArray, timeoutMs: Long = requestTimeoutMs): TeFrame =
        lock.withLock {
            val id = nextId
            nextId = (nextId + 1) and 0xFFF
            val waiter = CompletableDeferred<TeFrame>()
            pending[id] = waiter
            try {
                transport.send(Frame.encodeRequest(identityCode, id, command, payload))
                val frame = withTimeoutOrNull(timeoutMs) { waiter.await() }
                    ?: throw KoiiTimeoutException("Timeout waiting for command $command (id $id)")
                val st = frame.status ?: 0
                if (st != Te.STATUS_OK && st < 64) {
                    throw KoiiProtocolException("Device returned status $st for command $command", st)
                }
                frame
            } finally {
                pending.remove(id)
            }
        }

    private suspend fun file(payload: ByteArray, timeoutMs: Long = requestTimeoutMs): TeFrame =
        call(Te.CMD_FILE, payload, timeoutMs)

    // ---- file system ---------------------------------------------------------------------

    suspend fun list(nodeId: Int): List<FileEntry> {
        val all = ArrayList<FileEntry>()
        var page = 0
        while (true) {
            val entries = FileMsg.parseList(file(FileMsg.list(page, nodeId)).payload, page) ?: break
            all += entries
            page++
            if (page > 0xFFFF) break
        }
        return all
    }

    suspend fun info(nodeId: Int): FileInfo = FileMsg.parseInfo(file(FileMsg.info(nodeId)).payload)

    /**
     * Resolves a path like "/projects/P01/groups/A/pads/03" by walking names from the root (node 0).
     * Node ids are sparse (e.g. /sounds = 1000, /projects = 2000): never assume them.
     */
    suspend fun resolve(path: String): Int {
        var id = 0
        var walked = ""
        for (part in path.split('/').filter { it.isNotEmpty() }) {
            walked += "/$part"
            val cached = dirCache[walked]
            if (cached != null) { id = cached; continue }
            val child = list(id).firstOrNull { it.name == part }
                ?: throw KoiiProtocolException("Path not found: $walked")
            id = child.nodeId
            dirCache[walked] = id
        }
        return id
    }

    suspend fun download(nodeId: Int, onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }): Download {
        val head = FileMsg.parseGetInit(file(FileMsg.getInit(nodeId)).payload)
        val out = java.io.ByteArrayOutputStream(head.size.coerceIn(0, 64L * 1024 * 1024).toInt())
        var page = 0
        while (true) {
            val reply = FileMsg.parseGetData(file(FileMsg.getData(page)).payload)
            if (reply.data.isEmpty()) break
            out.write(reply.data)
            onProgress(out.size().toLong(), head.size)
            page = (reply.page + 1) and 0xFFFF
        }
        val bytes = out.toByteArray()
        if (head.size > 0 && bytes.size.toLong() != head.size) {
            throw KoiiProtocolException("Download size mismatch: expected ${head.size}, got ${bytes.size}")
        }
        return Download(nodeId, head.name, head.flags, bytes)
    }

    /**
     * Uploads [data] as a new file. Returns the node id assigned by the device. The wire filename is rewritten
     * by the device to NNN.pcm for sounds; set the display name via [setMetadata] afterwards.
     * [finalTimeoutMs] covers the closing empty chunk, which can take long for project archives (kokoko: 60 s).
     */
    suspend fun upload(
        name: String,
        data: ByteArray,
        parentId: Int,
        fileId: Int = 0,
        flags: Int = FileFlags.USER_SOUND,
        metadataJson: String? = null,
        finalTimeoutMs: Long = 15_000,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Int {
        val chunk = maxChunkPayloadLength(chunkSize)
        if (chunk <= 0) throw KoiiProtocolException("Device chunk size too small ($chunkSize); call connect() first")

        val assigned = try {
            FileMsg.parsePutInit(file(FileMsg.putInit(fileId, parentId, flags, data.size.toLong(), name, metadataJson)).payload)
        } catch (e: KoiiProtocolException) {
            if (flags == FileFlags.FACTORY_SOUND || e.status == null) throw e
            // Device may refuse the extra MOVE/PLAYBACK capabilities; retry with the observed factory flags.
            FileMsg.parsePutInit(file(FileMsg.putInit(fileId, parentId, FileFlags.FACTORY_SOUND, data.size.toLong(), name, metadataJson)).payload)
        }

        var off = 0
        var page = 0
        while (off < data.size) {
            val n = minOf(chunk, data.size - off)
            file(FileMsg.putData(page, data, off, n))
            off += n
            page = (page + 1) and 0xFFFF
            onProgress(off.toLong(), data.size.toLong())
        }
        file(FileMsg.putData(page, data, 0, 0), finalTimeoutMs)
        return assigned
    }

    suspend fun delete(nodeId: Int) {
        file(FileMsg.delete(nodeId))
    }

    /**
     * Moves [nodeId] under [parentId] with a new id ([newNodeId] = slot number). The reference tool tolerates a
     * timeout here and re-reads state afterwards, so callers should re-list on [KoiiTimeoutException].
     */
    suspend fun move(nodeId: Int, parentId: Int, newNodeId: Int): FileMsg.MoveReply =
        FileMsg.parseMove(file(FileMsg.move(nodeId, parentId, newNodeId), timeoutMs = 8_000).payload)

    suspend fun getMetadata(nodeId: Int): JsonObject {
        val sb = StringBuilder()
        var page = 0
        while (true) {
            val frag = FileMsg.parseMetadata(file(FileMsg.getMetadata(nodeId, page)).payload)
            sb.append(frag.text)
            if (frag.isLast || page > 255) break
            page++
        }
        val text = sb.toString().ifBlank { "{}" }
        return Json.parseToJsonElement(text).jsonObject
    }

    suspend fun setMetadata(nodeId: Int, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val limit = chunkSize - 8
        if (chunkSize == 0 || bytes.size + 1 <= limit) {
            file(FileMsg.setMetadata(nodeId, json))
            return
        }
        file(FileMsg.setMetadataPagedInit(nodeId, bytes.size.toLong()))
        val chunk = maxChunkPayloadLength(chunkSize)
        var off = 0
        var page = 0
        while (off < bytes.size) {
            val n = minOf(chunk, bytes.size - off)
            file(FileMsg.setMetadataPagedData(page++, bytes, off, n))
            off += n
        }
        file(FileMsg.setMetadataPagedData(page, bytes, 0, 0))
    }

    /** 1-second style preview straight from the device (unverified on hardware). */
    suspend fun playPreview(nodeId: Int, lengthMs: Long = 1000) {
        file(FileMsg.playback(FileMsg.PLAYBACK_START, nodeId, 0, lengthMs))
    }

    suspend fun stopPreview(nodeId: Int) {
        file(FileMsg.playback(FileMsg.PLAYBACK_STOP, nodeId, 0, 0))
    }

    override fun close() {
        collector.cancel()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }
}
