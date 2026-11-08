package de.koii.protocol

object Te {
    val MANUFACTURER = byteArrayOf(0x00, 0x20, 0x76)
    const val SYSEX_START = 0xF0
    const val SYSEX_END = 0xF7
    const val MARKER_PROTOCOL = 0x40
    /** Byte 5 of device debug-log frames. */
    const val MARKER_LOG = 0x33

    const val BIT_IS_REQUEST = 0x40
    const val BIT_REQUEST_ID = 0x20

    const val CMD_GREET = 1
    const val CMD_ECHO = 2
    const val CMD_FILE = 5

    const val STATUS_OK = 0
}

enum class FrameKind { REQUEST, RESPONSE, DEVICE_LOG }

class TeFrame(
    val kind: FrameKind,
    val identityCode: Int,
    val hasRequestId: Boolean,
    val requestId: Int,
    val command: Int,
    val status: Int?,
    val payload: ByteArray,
)

object Frame {
    fun encodeRequest(identityCode: Int, requestId: Int, command: Int, payload: ByteArray): ByteArray =
        encode(identityCode, requestId, command, status = null, payload = payload)

    /** Builds a device→host frame. Only used by simulators and tests. */
    fun encodeResponse(identityCode: Int, requestId: Int, command: Int, status: Int, payload: ByteArray): ByteArray =
        encode(identityCode, requestId, command, status, payload)

    private fun encode(identityCode: Int, requestId: Int, command: Int, status: Int?, payload: ByteArray): ByteArray {
        val packed = SevenBit.pack(payload)
        val hdr = 9 + (if (status != null) 1 else 0)
        val out = ByteArray(hdr + packed.size + 1)
        out[0] = Te.SYSEX_START.toByte()
        out[1] = Te.MANUFACTURER[0]; out[2] = Te.MANUFACTURER[1]; out[3] = Te.MANUFACTURER[2]
        out[4] = identityCode.toByte()
        out[5] = Te.MARKER_PROTOCOL.toByte()
        val isRequest = status == null
        out[6] = ((if (isRequest) Te.BIT_IS_REQUEST else 0) or Te.BIT_REQUEST_ID or ((requestId shr 7) and 31)).toByte()
        out[7] = (requestId and 127).toByte()
        out[8] = command.toByte()
        if (status != null) out[9] = status.toByte()
        System.arraycopy(packed, 0, out, hdr, packed.size)
        out[out.size - 1] = Te.SYSEX_END.toByte()
        return out
    }

    /** Parses a complete SysEx message. Returns null for foreign or malformed frames. */
    fun parse(raw: ByteArray): TeFrame? {
        if (raw.size < 9) return null
        if ((raw[0].toInt() and 0xFF) != Te.SYSEX_START) return null
        if (raw[1] != Te.MANUFACTURER[0] || raw[2] != Te.MANUFACTURER[1] || raw[3] != Te.MANUFACTURER[2]) return null
        if ((raw[raw.size - 1].toInt() and 0xFF) != Te.SYSEX_END) return null

        val marker = raw[5].toInt() and 0xFF
        val identity = raw[4].toInt() and 0xFF
        if (marker == Te.MARKER_LOG) {
            val payload = SevenBit.unpack(raw, 6, raw.size - 1)
            return TeFrame(FrameKind.DEVICE_LOG, identity, false, 0, 0, null, payload)
        }
        if (marker != Te.MARKER_PROTOCOL) return null

        val b6 = raw[6].toInt() and 0xFF
        val isRequest = (b6 and Te.BIT_IS_REQUEST) != 0
        val hasId = (b6 and Te.BIT_REQUEST_ID) != 0
        val id = if (hasId) ((b6 and 31) shl 7) or (raw[7].toInt() and 127) else 0
        var offset = 9
        var status: Int? = null
        if (!isRequest) {
            if (raw.size <= offset) return null
            status = raw[offset].toInt() and 0xFF
            offset++
        }
        val payload = SevenBit.unpack(raw, offset, raw.size - 1)
        return TeFrame(
            if (isRequest) FrameKind.REQUEST else FrameKind.RESPONSE,
            identity, hasId, id, raw[8].toInt() and 0xFF, status, payload,
        )
    }
}

object Identity {
    val REQUEST = byteArrayOf(0xF0.toByte(), 0x7E, 0x7F, 0x06, 0x01, 0xF7.toByte())

    /** The reply is exactly 17 bytes: `F0 7E id 06 02 00 20 76 fam fam mod mod ver.. F7`. */
    fun parseReply(raw: ByteArray): IdentityReply? {
        if (raw.size != 17) return null
        if ((raw[0].toInt() and 0xFF) != 0xF0 || raw[1].toInt() != 0x7E) return null
        if (raw[5] != Te.MANUFACTURER[0] || raw[6] != Te.MANUFACTURER[1] || raw[7] != Te.MANUFACTURER[2]) return null
        val family = (raw[8].toInt() and 0x7F) or ((raw[9].toInt() and 0x7F) shl 7)
        val model = (raw[10].toInt() and 0x7F) or ((raw[11].toInt() and 0x7F) shl 7)
        val sku = "TE%03dAS%03d".format(family, model)
        return IdentityReply(raw[2].toInt() and 0xFF, sku)
    }
}
