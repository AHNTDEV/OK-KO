package de.koii.audio

object AiffCodec {
    fun isAiff(b: ByteArray): Boolean =
        b.size >= 12 && b.tag(0) == "FORM" && (b.tag(8) == "AIFF" || b.tag(8) == "AIFC")

    fun decode(b: ByteArray): PcmAudio {
        if (!isAiff(b)) throw UnsupportedAudioException("Not an AIFF file")
        val aifc = b.tag(8) == "AIFC"
        var pos = 12
        var ch = 0; var bits = 0; var rate = 0.0
        var littleEndian = false
        var isFloat = false
        var dataOff = -1; var dataLen = 0
        while (pos + 8 <= b.size) {
            val id = b.tag(pos)
            var size = b.be32(pos + 4)
            val body = pos + 8
            if (body + size > b.size) size = (b.size - body).toLong()
            when (id) {
                "COMM" -> {
                    ch = b.be16(body); bits = b.be16(body + 6); rate = extended(b, body + 8)
                    if (aifc && size >= 22) {
                        when (b.tag(body + 18)) {
                            "NONE", "twos" -> {}
                            "sowt" -> littleEndian = true
                            "fl32", "FL32" -> isFloat = true
                            else -> throw UnsupportedAudioException("Compressed AIFF-C (${b.tag(body + 18)}) not supported")
                        }
                    }
                }
                "SSND" -> if (dataOff < 0) {
                    val offset = b.be32(body).toInt()
                    dataOff = body + 8 + offset
                    dataLen = (size - 8 - offset).toInt()
                }
            }
            pos = body + size.toInt() + (size.toInt() and 1)
        }
        if (dataOff < 0 || ch == 0 || rate <= 0) throw UnsupportedAudioException("AIFF has no COMM/SSND chunk")
        val bytesPer = (bits + 7) / 8
        val count = dataLen / bytesPer
        val out = FloatArray(count / ch * ch)
        var o = dataOff
        for (i in out.indices) {
            out[i] = when {
                isFloat && bits == 32 -> java.lang.Float.intBitsToFloat(b.be32(o).toInt())
                bits == 8 -> b[o] / 128f
                bits == 16 -> (if (littleEndian) b.le16(o) else b.be16(o)).toShort() / 32768f
                bits == 24 -> ((b[o].toInt() shl 16) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() and 0xFF)) / 8388608f
                bits == 32 -> b.be32(o).toInt() / 2147483648f
                else -> throw UnsupportedAudioException("Unsupported AIFF depth $bits")
            }
            o += bytesPer
        }
        return PcmAudio(rate.toInt(), ch, out)
    }

    /** 80-bit IEEE extended → double. */
    private fun extended(b: ByteArray, o: Int): Double {
        val exp = b.be16(o) and 0x7FFF
        val hi = b.be32(o + 2).toDouble()
        val lo = b.be32(o + 6).toDouble()
        val mant = hi * 4294967296.0 + lo
        return mant * Math.pow(2.0, (exp - 16383 - 63).toDouble())
    }
}
