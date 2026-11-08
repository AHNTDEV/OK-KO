package de.koii.protocol

/** Builders and parsers for command 5 (FILE). Layouts follow docs/protocol.md of lexfrei/kokoko. */
object FileMsg {
    const val SUB_INIT = 1
    const val SUB_PUT = 2
    const val SUB_GET = 3
    const val SUB_LIST = 4
    const val SUB_PLAYBACK = 5
    const val SUB_DELETE = 6
    const val SUB_METADATA = 7
    const val SUB_INFO = 11
    const val SUB_MOVE = 12

    const val PLAYBACK_START = 1
    const val PLAYBACK_STOP = 2

    /** The reference tool asks for 4 MiB max response length. */
    const val MAX_RESPONSE_LENGTH = 4L * 1024 * 1024

    fun init(subscribe: Boolean): ByteArray =
        ByteWriter().u8(SUB_INIT).u8(if (subscribe) 1 else 0).u32(MAX_RESPONSE_LENGTH).toByteArray()

    /** Reply: first byte ignored, then chunk_size:u32. */
    fun parseInit(payload: ByteArray): Int {
        val r = ByteReader(payload)
        r.skip(1)
        return r.u32().toInt()
    }

    fun putInit(fileId: Int, parentId: Int, flags: Int, size: Long, filename: String, metadataJson: String?): ByteArray {
        val w = ByteWriter().u8(SUB_PUT).u8(0).u8(flags).u16(fileId).u16(parentId).u32(size)
        w.zstring(truncateUtf8(filename, 54))
        if (metadataJson != null) w.zstring(metadataJson)
        return w.toByteArray()
    }

    fun parsePutInit(payload: ByteArray): Int = ByteReader(payload).u16()

    fun putData(page: Int, data: ByteArray, off: Int, len: Int): ByteArray =
        ByteWriter(len + 8).u8(SUB_PUT).u8(1).u16(page).bytes(data, off, len).toByteArray()

    /**
     * Offset only. Do NOT add the "extra args" form: a guessed layout preceded a firmware crash
     * (siren screen, code 69) on EP-133 os 2.5.1.
     */
    fun getInit(fileId: Int, offset: Long = 0): ByteArray =
        ByteWriter().u8(SUB_GET).u8(0).u16(fileId).u32(offset).toByteArray()

    class GetInitReply(val fileId: Int, val flags: Int, val size: Long, val name: String)

    fun parseGetInit(payload: ByteArray): GetInitReply {
        val r = ByteReader(payload)
        return GetInitReply(r.u16(), r.u8(), r.u32(), r.zstring())
    }

    fun getData(page: Int): ByteArray = ByteWriter().u8(SUB_GET).u8(1).u16(page).toByteArray()

    class GetDataReply(val page: Int, val data: ByteArray)

    fun parseGetData(payload: ByteArray): GetDataReply {
        val r = ByteReader(payload)
        return GetDataReply(r.u16(), r.rest())
    }

    fun list(page: Int, nodeId: Int): ByteArray =
        ByteWriter().u8(SUB_LIST).u16(page).u16(nodeId).toByteArray()

    /** Returns null when the listing is finished (reply of ≤ 2 bytes). */
    fun parseList(payload: ByteArray, expectedPage: Int): List<FileEntry>? {
        if (payload.size <= 2) return null
        val r = ByteReader(payload)
        val page = r.u16()
        if (page != expectedPage) throw KoiiProtocolException("LIST page mismatch: expected $expectedPage, got $page")
        val out = ArrayList<FileEntry>()
        while (r.remaining >= 8) {
            val id = r.u16()
            val flags = r.u8()
            val size = r.u32()
            val name = r.zstring()
            out += FileEntry(id, flags, size, name)
        }
        return out
    }

    fun info(fileId: Int): ByteArray = ByteWriter().u8(SUB_INFO).u16(fileId).toByteArray()

    fun parseInfo(payload: ByteArray): FileInfo {
        val r = ByteReader(payload)
        return FileInfo(r.u16(), r.u16(), r.u8(), r.u32(), r.zstring())
    }

    fun delete(fileId: Int): ByteArray = ByteWriter().u8(SUB_DELETE).u16(fileId).toByteArray()

    fun playback(action: Int, fileId: Int, offset: Long, length: Long): ByteArray =
        ByteWriter().u8(SUB_PLAYBACK).u8(action).u16(fileId).u32(offset).u32(length).toByteArray()

    fun setMetadata(fileId: Int, json: String): ByteArray =
        ByteWriter().u8(SUB_METADATA).u8(1).u16(fileId).zstring(json).toByteArray()

    fun getMetadata(fileId: Int, page: Int, key: String? = null): ByteArray {
        val w = ByteWriter().u8(SUB_METADATA).u8(2).u16(fileId).u16(page)
        if (key != null) w.zstring(key)
        return w.toByteArray()
    }

    class MetadataFragment(val page: Int, val text: String, val isLast: Boolean)

    fun parseMetadata(payload: ByteArray): MetadataFragment {
        val r = ByteReader(payload)
        val page = r.u16()
        val last = payload.isNotEmpty() && payload.last().toInt() == 0
        return MetadataFragment(page, r.zstring(), last)
    }

    fun setMetadataPagedInit(fileId: Int, totalSize: Long): ByteArray =
        ByteWriter().u8(SUB_METADATA).u8(4).u8(0).u16(fileId).u32(totalSize).toByteArray()

    fun setMetadataPagedData(page: Int, data: ByteArray, off: Int, len: Int): ByteArray =
        ByteWriter(len + 8).u8(SUB_METADATA).u8(4).u8(1).u16(page).bytes(data, off, len).toByteArray()

    fun move(fileId: Int, parentId: Int, newFileId: Int): ByteArray =
        ByteWriter().u8(SUB_MOVE).u16(fileId).u16(parentId).u16(newFileId).toByteArray()

    class MoveReply(val oldId: Int, val parentId: Int, val newId: Int)

    fun parseMove(payload: ByteArray): MoveReply {
        val r = ByteReader(payload)
        return MoveReply(r.u16(), r.u16(), r.u16())
    }

    /** Device-initiated events (INIT with subscribe flag). Payload is the unpacked frame payload. */
    fun parseEvent(payload: ByteArray): FileEvent? {
        if (payload.isEmpty()) return null
        return try {
            val r = ByteReader(payload)
            when (r.u8()) {
                3 -> FileEvent.MetadataUpdated(r.u16(), r.zstring())
                8, 9 -> {
                    val kind = payload[0].toInt()
                    val id = r.u16()
                    r.skip(2)
                    val size = r.u32()
                    val name = r.zstring()
                    if (kind == 8) FileEvent.Added(id, size, name) else FileEvent.Updated(id, size, name)
                }
                10 -> FileEvent.Deleted(r.u16())
                13 -> FileEvent.Moved(r.u16(), r.u16(), r.u16())
                else -> null
            }
        } catch (_: KoiiProtocolException) {
            null
        }
    }

    private fun truncateUtf8(s: String, maxBytes: Int): String {
        if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
        val sb = StringBuilder()
        var bytes = 0
        for (ch in s) {
            val n = ch.toString().toByteArray(Charsets.UTF_8).size
            if (bytes + n > maxBytes) break
            sb.append(ch); bytes += n
        }
        return sb.toString()
    }
}
