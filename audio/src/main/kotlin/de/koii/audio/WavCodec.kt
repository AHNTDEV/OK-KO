package de.koii.audio

import java.io.ByteArrayOutputStream

class WavInfo(
    val formatTag: Int,
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val dataOffset: Int,
    val dataLength: Int,
    val rootNote: Int?,
    val loopStart: Long?,
    val loopEnd: Long?,
) {
    val isPcm: Boolean get() = formatTag == 1
    val isFloat: Boolean get() = formatTag == 3
}

object WavCodec {
    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    fun isWav(b: ByteArray): Boolean = b.size >= 12 && b.tag(0) == "RIFF" && b.tag(8) == "WAVE"

    fun parse(b: ByteArray): WavInfo {
        if (!isWav(b)) throw UnsupportedAudioException("Not a RIFF/WAVE file")
        var pos = 12
        var tag = 0; var ch = 0; var rate = 0; var bits = 0
        var dataOff = -1; var dataLen = 0
        var root: Int? = null; var loopStart: Long? = null; var loopEnd: Long? = null
        while (pos + 8 <= b.size) {
            val id = b.tag(pos)
            var size = b.le32(pos + 4)
            val body = pos + 8
            if (body + size > b.size) size = (b.size - body).toLong() // truncated or streaming WAV
            when (id) {
                "fmt " -> {
                    tag = b.le16(body); ch = b.le16(body + 2); rate = b.le32(body + 4).toInt(); bits = b.le16(body + 14)
                    if (tag == FORMAT_EXTENSIBLE && size >= 26) tag = b.le16(body + 24)
                }
                "data" -> if (dataOff < 0) { dataOff = body; dataLen = size.toInt() }
                "smpl" -> if (size >= 36) {
                    root = b.le32(body + 12).toInt().takeIf { it in 1..127 }
                    val loops = b.le32(body + 28).toInt()
                    if (loops > 0 && size >= 36 + 24) {
                        loopStart = b.le32(body + 36 + 8)
                        loopEnd = b.le32(body + 36 + 12)
                    }
                }
            }
            pos = body + size.toInt() + (size.toInt() and 1)
        }
        if (dataOff < 0 || ch == 0) throw UnsupportedAudioException("WAV has no fmt/data chunk")
        return WavInfo(tag, ch, rate, bits, dataOff, dataLen, root, loopStart, loopEnd)
    }

    fun decode(b: ByteArray, info: WavInfo = parse(b)): PcmAudio {
        val bytesPer = info.bitsPerSample / 8
        if (bytesPer == 0) throw UnsupportedAudioException("Bad bit depth ${info.bitsPerSample}")
        val count = info.dataLength / bytesPer
        val frames = count / info.channels
        val out = FloatArray(frames * info.channels)
        var o = info.dataOffset
        for (i in out.indices) {
            out[i] = when {
                info.isPcm && info.bitsPerSample == 8 -> ((b[o].toInt() and 0xFF) - 128) / 128f
                info.isPcm && info.bitsPerSample == 16 -> b.le16(o).toShort() / 32768f
                info.isPcm && info.bitsPerSample == 24 ->
                    (((b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or (b[o + 2].toInt() shl 16))) / 8388608f
                info.isPcm && info.bitsPerSample == 32 -> b.le32(o).toInt() / 2147483648f
                info.isFloat && info.bitsPerSample == 32 -> java.lang.Float.intBitsToFloat(b.le32(o).toInt())
                info.isFloat && info.bitsPerSample == 64 -> java.lang.Double.longBitsToDouble(
                    b.le32(o) or (b.le32(o + 4) shl 32),
                ).toFloat()
                else -> throw UnsupportedAudioException("Unsupported WAV format ${info.formatTag}/${info.bitsPerSample} bit")
            }
            o += bytesPer
        }
        return PcmAudio(info.sampleRate, info.channels, out)
    }

    /** Wraps raw s16le PCM into a canonical 44-byte-header WAV. */
    fun wrapPcm16(pcm: ByteArray, channels: Int, sampleRate: Int, rootNote: Int? = null, loopStart: Long? = null, loopEnd: Long? = null): ByteArray {
        val smpl = if (rootNote != null || loopStart != null) smplChunk(rootNote ?: 60, loopStart, loopEnd) else ByteArray(0)
        val out = ByteArrayOutputStream(pcm.size + 44 + smpl.size)
        fun w32(v: Long) { for (s in 0..24 step 8) out.write(((v shr s) and 0xFF).toInt()) }
        fun w16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        out.write("RIFF".toByteArray()); w32(36L + pcm.size + smpl.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); w32(16); w16(FORMAT_PCM); w16(channels); w32(sampleRate.toLong())
        w32(sampleRate.toLong() * channels * 2); w16(channels * 2); w16(16)
        out.write("data".toByteArray()); w32(pcm.size.toLong()); out.write(pcm)
        out.write(smpl)
        return out.toByteArray()
    }

    private fun smplChunk(root: Int, loopStart: Long?, loopEnd: Long?): ByteArray {
        val hasLoop = loopStart != null && loopEnd != null
        val body = ByteArrayOutputStream()
        fun w32(v: Long) { for (s in 0..24 step 8) body.write(((v shr s) and 0xFF).toInt()) }
        w32(0); w32(0); w32(0); w32(root.toLong()); w32(0); w32(0); w32(0)
        w32(if (hasLoop) 1 else 0); w32(0)
        if (hasLoop) { w32(0); w32(0); w32(loopStart); w32(loopEnd); w32(0); w32(0) }
        val b = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("smpl".toByteArray())
        for (s in 0..24 step 8) out.write((b.size shr s) and 0xFF)
        out.write(b)
        return out.toByteArray()
    }

    fun encodePcm16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = Math.round(samples[i].coerceIn(-1f, 1f) * 32767f)
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }
}
