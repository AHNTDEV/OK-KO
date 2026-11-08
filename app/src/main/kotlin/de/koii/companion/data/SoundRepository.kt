package de.koii.companion.data

import de.koii.audio.AudioConverter
import de.koii.audio.MediaDecoder
import de.koii.audio.PreparedSound
import de.koii.audio.WavCodec
import de.koii.protocol.FileEntry
import de.koii.protocol.FileEvent
import de.koii.protocol.KoiiProtocolException
import de.koii.protocol.KoiiTimeoutException
import de.koii.protocol.Names
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Sound slots on the device. Slot number == node id under /sounds (sounds are numbered from 1). */
class SoundRepository(
    private val session: DeviceSession,
    private val trash: TrashRepository,
    private val scope: CoroutineScope,
) {
    companion object {
        const val SLOT_COUNT = 999
    }

    private val _slots = MutableStateFlow<Map<Int, FileEntry>>(emptyMap())
    val slots: StateFlow<Map<Int, FileEntry>> = _slots

    private val _names = MutableStateFlow<Map<Int, String>>(emptyMap())
    /** Display names (metadata "name"), loaded lazily per page. */
    val names: StateFlow<Map<Int, String>> = _names

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val opLock = Mutex()
    private var refreshJob: Job? = null

    init {
        scope.launch {
            session.link.collect { l ->
                if (l is Link.Online) refresh() else { _slots.value = emptyMap(); _names.value = emptyMap() }
            }
        }
        scope.launch {
            session.events.collect { e ->
                if (e is FileEvent.MetadataUpdated) _names.update { it - e.nodeId }
                scheduleRefresh()
            }
        }
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch { delay(400); refresh() }
    }

    suspend fun refresh() {
        val c = session.client ?: return
        _loading.value = true
        try {
            val entries = c.list(session.soundsNodeId()).filter { !it.isDirectory }
            _slots.value = entries.associateBy { it.nodeId }
        } catch (_: KoiiTimeoutException) {
            // device busy; the next event or manual refresh retries
        } finally {
            _loading.value = false
        }
    }

    /** Loads display names for [ids] that are occupied and not cached yet. */
    suspend fun loadNames(ids: Collection<Int>) {
        val c = session.client ?: return
        for (id in ids) {
            if (id in _names.value || id !in _slots.value) continue
            val name = runCatching { c.getMetadata(id)["name"]?.jsonPrimitive?.content }.getOrNull()
            _names.update { it + (id to (name ?: _slots.value[id]?.name.orEmpty())) }
        }
    }

    fun displayName(id: Int): String = _names.value[id] ?: _slots.value[id]?.name.orEmpty()

    fun freeSlots(from: Int = 1): List<Int> = (from..SLOT_COUNT).filter { it !in _slots.value }

    // ---- editing -------------------------------------------------------------------------

    suspend fun rename(id: Int, raw: String) {
        val name = Names.normalizeSlotName(raw).ifEmpty { throw IllegalArgumentException("Empty name") }
        session.requireClient().setMetadata(id, buildJsonObject { put("name", name) }.toString())
        _names.update { it + (id to name) }
    }

    suspend fun setMetadata(id: Int, json: JsonObject) {
        session.requireClient().setMetadata(id, json.toString())
    }

    suspend fun preview(id: Int) = session.requireClient().playPreview(id)

    /** Moves [from] to an empty slot [to], or swaps both when [to] is occupied. */
    suspend fun moveOrSwap(from: Int, to: Int) {
        if (from == to) return
        opLock.withLock {
            val c = session.requireClient()
            val parent = session.soundsNodeId()
            try {
                if (to !in _slots.value) {
                    c.move(from, parent, to)
                } else {
                    val temp = freeSlots().lastOrNull() ?: throw IllegalStateException("No free slot for the swap")
                    c.move(from, parent, temp)
                    c.move(to, parent, from)
                    c.move(temp, parent, to)
                }
            } catch (e: KoiiTimeoutException) {
                // The reference tool tolerates a move timeout and re-reads state.
            } finally {
                _names.update { it - from - to }
                refresh()
            }
        }
    }

    /** Deletes sounds from the device after keeping a copy in the app trash. */
    suspend fun deleteToTrash(ids: List<Int>, progress: (Long, Long) -> Unit = { _, _ -> }) = opLock.withLock {
        val c = session.requireClient()
        ids.forEachIndexed { i, id ->
            val dl = c.download(id)
            val meta = runCatching { c.getMetadata(id).toString() }.getOrDefault("{}")
            val name = _names.value[id] ?: runCatching { c.getMetadata(id)["name"]?.jsonPrimitive?.content }.getOrNull() ?: dl.name
            trash.add(id, name, dl.bytes, meta)
            c.delete(id)
            progress((i + 1).toLong(), ids.size.toLong())
        }
        _names.update { it - ids.toSet() }
        refresh()
        session.refreshStorage()
    }

    /** Puts a trashed sound back into its original slot if free, otherwise the first free slot. */
    suspend fun restore(item: TrashItem): Int = opLock.withLock {
        val target = if (item.slot !in _slots.value) item.slot else freeSlots().firstOrNull()
            ?: throw IllegalStateException("No free slot")
        val c = session.requireClient()
        val id = c.upload(item.name, trash.read(item), session.soundsNodeId(), fileId = target)
        c.setMetadata(id, item.metadata)
        trash.remove(item)
        refresh()
        session.refreshStorage()
        id
    }

    /**
     * Replaces the sound in [slot] with [sound]: the old one goes to the app trash first, the slot keeps its
     * number and the old metadata (root note, pitch, envelope …) except values that depend on the audio itself.
     */
    suspend fun replace(slot: Int, sound: PreparedSound, displayName: String, oldMeta: JsonObject?): Int = opLock.withLock {
        val c = session.requireClient()
        val old = c.download(slot)
        val oldJson = oldMeta ?: runCatching { c.getMetadata(slot) }.getOrDefault(JsonObject(emptyMap()))
        trash.add(slot, oldJson["name"]?.jsonPrimitive?.content ?: old.name, old.bytes, oldJson.toString())
        c.delete(slot)
        val name = Names.normalizeSlotName(displayName).ifEmpty { "sample" }
        val id = c.upload(name, sound.pcm, session.soundsNodeId(), fileId = slot)
        val drop = setOf("channels", "samplerate", "name", "sound.loopstart", "sound.loopend", "regions")
        val merged = buildJsonObject {
            oldJson.forEach { (k, v) -> if (k !in drop) put(k, v) }
            put("channels", sound.channels)
            put("samplerate", sound.sampleRate)
            put("name", name)
        }
        c.setMetadata(id, merged.toString())
        _names.update { it + (id to name) }
        refresh()
        session.refreshStorage()
        id
    }

    // ---- transfers -----------------------------------------------------------------------

    /** Decodes and converts a file off the main thread; MediaDecoder handles non-WAV/AIFF formats. */
    suspend fun prepare(bytes: ByteArray): PreparedSound = withContext(Dispatchers.Default) {
        AudioConverter.prepare(bytes, MediaDecoder::decode)
    }

    suspend fun upload(
        sound: PreparedSound,
        displayName: String,
        slot: Int?,
        progress: (Long, Long) -> Unit = { _, _ -> },
    ): Int = opLock.withLock {
        val c = session.requireClient()
        val name = Names.normalizeSlotName(displayName).ifEmpty { "sample" }
        val meta = buildJsonObject {
            put("channels", sound.channels)
            put("samplerate", sound.sampleRate)
            put("name", name)
            sound.rootNote?.let { put("sound.rootnote", it) }
            sound.loopStart?.let { put("sound.loopstart", it) }
            sound.loopEnd?.let { put("sound.loopend", it) }
        }
        val id = c.upload(name, sound.pcm, session.soundsNodeId(), fileId = slot ?: 0, onProgress = progress)
        c.setMetadata(id, meta.toString())
        _names.update { it + (id to name) }
        refresh()
        session.refreshStorage()
        id
    }

    /** Device sound as a WAV file (rate/channels from metadata, root note and loop points embedded). */
    suspend fun downloadAsWav(id: Int, progress: (Long, Long) -> Unit = { _, _ -> }): Pair<String, ByteArray> {
        val c = session.requireClient()
        val dl = c.download(id, progress)
        val meta = runCatching { c.getMetadata(id) }.getOrDefault(JsonObject(emptyMap()))
        val rate = meta["samplerate"]?.jsonPrimitive?.intOrNull ?: AudioConverter.TARGET_RATE
        val ch = meta["channels"]?.jsonPrimitive?.intOrNull ?: 1
        val root = meta["sound.rootnote"]?.jsonPrimitive?.intOrNull
        val ls = meta["sound.loopstart"]?.jsonPrimitive?.intOrNull?.toLong()
        val le = meta["sound.loopend"]?.jsonPrimitive?.intOrNull?.toLong()
        val name = meta["name"]?.jsonPrimitive?.content ?: "%03d".format(id)
        return name to WavCodec.wrapPcm16(dl.bytes, ch, rate, root, ls, le)
    }
}
