package de.koii.audio

/** Interleaved float samples in -1..1. */
class PcmAudio(val sampleRate: Int, val channels: Int, val samples: FloatArray) {
    val frames: Int get() = samples.size / channels
    val seconds: Double get() = frames.toDouble() / sampleRate
}

class UnsupportedAudioException(message: String) : Exception(message)

/** Result of preparing a file for the K.O. II: raw s16le PCM at the device rate. */
class PreparedSound(
    val pcm: ByteArray,
    val channels: Int,
    val sampleRate: Int,
    /** True when the source WAV was already s16 / 46875 Hz / 1–2 ch and was sent untouched. */
    val fastPath: Boolean,
    val rootNote: Int? = null,
    val loopStart: Long? = null,
    val loopEnd: Long? = null,
) {
    val frames: Int get() = pcm.size / (2 * channels)
    val seconds: Double get() = frames.toDouble() / sampleRate
}

internal fun ByteArray.le16(o: Int): Int = (this[o].toInt() and 0xFF) or ((this[o + 1].toInt() and 0xFF) shl 8)
internal fun ByteArray.le32(o: Int): Long = le16(o).toLong() or (le16(o + 2).toLong() shl 16)
internal fun ByteArray.be16(o: Int): Int = ((this[o].toInt() and 0xFF) shl 8) or (this[o + 1].toInt() and 0xFF)
internal fun ByteArray.be32(o: Int): Long = (be16(o).toLong() shl 16) or be16(o + 2).toLong()
internal fun ByteArray.tag(o: Int): String = String(this, o, 4, Charsets.ISO_8859_1)
