package de.koii.protocol

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Minimal in-memory EP-133 simulator speaking the documented wire protocol. */
class FakeDevice(private val chunkSize: Int = 512) : MidiTransport {
    private val _in = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1024)
    override val incoming: SharedFlow<ByteArray> = _in.asSharedFlow()

    class Node(val id: Int, val parent: Int, var flags: Int, var name: String, var data: ByteArray = ByteArray(0)) {
        var meta: String = "{}"
    }

    val nodes = LinkedHashMap<Int, Node>().apply {
        put(1000, Node(1000, 0, FileFlags.DIR, "sounds"))
        put(2000, Node(2000, 0, FileFlags.DIR, "projects"))
    }
    private var putBuf: java.io.ByteArrayOutputStream? = null
    private var putNode: Node? = null
    private var getNode: Node? = null
    val identityCode = 0x2A

    override suspend fun send(message: ByteArray) {
        if (message.contentEquals(Identity.REQUEST)) {
            _in.emit(byteArrayOf(0xF0.toByte(), 0x7E, identityCode.toByte(), 0x06, 0x02, 0x00, 0x20, 0x76,
                32, 0, 2, 0, 1, 0, 0, 0, 0xF7.toByte()))
            return
        }
        val f = Frame.parse(message) ?: return
        val (status, payload) = try { handle(f) } catch (e: Exception) { 1 to ByteArray(0) }
        _in.emit(Frame.encodeResponse(identityCode, f.requestId, f.command, status, payload))
    }

    private fun handle(f: TeFrame): Pair<Int, ByteArray> {
        if (f.command == Te.CMD_GREET) {
            return 0 to "mode:normal;os_version:2.5.1;serial:FAKE;sku:TE032AS002;base_sku:TE032AS001;".toByteArray()
        }
        val r = ByteReader(f.payload)
        return when (r.u8()) {
            FileMsg.SUB_INIT -> 0 to ByteWriter().u8(0).u32(chunkSize.toLong()).toByteArray()
            FileMsg.SUB_LIST -> {
                val page = r.u16(); val parent = r.u16()
                if (page > 0) return 0 to ByteArray(2)
                val w = ByteWriter().u16(page)
                nodes.values.filter { it.parent == parent }.sortedBy { it.id }.forEach {
                    w.u16(it.id).u8(it.flags).u32(it.data.size.toLong()).zstring(it.name)
                }
                0 to w.toByteArray()
            }
            FileMsg.SUB_PUT -> when (r.u8()) {
                0 -> {
                    val flags = r.u8(); var id = r.u16(); val parent = r.u16(); r.u32(); val name = r.zstring()
                    if (id == 0) id = (1..999).first { it !in nodes }
                    val node = Node(id, parent, flags, "%03d.pcm".format(id))
                    putNode = node; putBuf = java.io.ByteArrayOutputStream()
                    node.meta = """{"name":"$name"}"""
                    0 to ByteWriter().u16(id).toByteArray()
                }
                else -> {
                    r.u16(); val chunk = r.rest()
                    if (chunk.isEmpty()) { putNode!!.data = putBuf!!.toByteArray(); nodes[putNode!!.id] = putNode!! }
                    else putBuf!!.write(chunk)
                    0 to ByteArray(0)
                }
            }
            FileMsg.SUB_GET -> when (r.u8()) {
                0 -> {
                    val n = nodes[r.u16()] ?: return 16 to ByteArray(0)
                    getNode = n
                    0 to ByteWriter().u16(n.id).u8(n.flags).u32(n.data.size.toLong()).zstring(n.name).toByteArray()
                }
                else -> {
                    val page = r.u16(); val n = getNode!!
                    val per = maxChunkPayloadLength(chunkSize)
                    val start = page * per
                    val end = minOf(n.data.size, start + per)
                    val data = if (start >= n.data.size) ByteArray(0) else n.data.copyOfRange(start, end)
                    0 to ByteWriter().u16(page).bytes(data).toByteArray()
                }
            }
            FileMsg.SUB_DELETE -> if (nodes.remove(r.u16()) != null) 0 to ByteArray(0) else 16 to ByteArray(0)
            FileMsg.SUB_INFO -> {
                val n = nodes[r.u16()] ?: return 16 to ByteArray(0)
                0 to ByteWriter().u16(n.id).u16(n.parent).u8(n.flags).u32(n.data.size.toLong()).zstring(n.name).toByteArray()
            }
            FileMsg.SUB_MOVE -> {
                val id = r.u16(); val parent = r.u16(); val newId = r.u16()
                val n = nodes.remove(id) ?: return 16 to ByteArray(0)
                if (newId in nodes) { nodes[id] = n; return 16 to ByteArray(0) }
                val moved = Node(newId, parent, n.flags, n.name, n.data).also { it.meta = n.meta }
                nodes[newId] = moved
                0 to ByteWriter().u16(id).u16(parent).u16(newId).toByteArray()
            }
            FileMsg.SUB_METADATA -> when (r.u8()) {
                1 -> { nodes[r.u16()]?.meta = r.zstring(); 0 to ByteArray(0) }
                2 -> {
                    val n = nodes[r.u16()] ?: return 16 to ByteArray(0)
                    0 to ByteWriter().u16(r.u16()).zstring(n.meta).toByteArray()
                }
                else -> 3 to ByteArray(0)
            }
            else -> 2 to ByteArray(0)
        }
    }

    override fun close() {}
}
