package de.koii.audio

/** Turns arbitrary audio files into what the K.O. II takes: s16le, 46875 Hz, 1–2 channels, ≤ 40 s. */
object AudioConverter {
    const val TARGET_RATE = 46875
    const val MAX_SECONDS = 40.0

    /**
     * @param fallbackDecoder decodes formats this module cannot (mp3, m4a, flac, …), e.g. [MediaDecoder]
     */
    fun prepare(bytes: ByteArray, fallbackDecoder: ((ByteArray) -> PcmAudio)? = null): PreparedSound {
        if (WavCodec.isWav(bytes)) {
            val info = WavCodec.parse(bytes)
            if (info.isPcm && info.bitsPerSample == 16 && info.sampleRate == TARGET_RATE && info.channels in 1..2) {
                // Fast path: send the raw data chunk untouched.
                val pcm = bytes.copyOfRange(info.dataOffset, info.dataOffset + info.dataLength - info.dataLength % (2 * info.channels))
                return PreparedSound(pcm, info.channels, TARGET_RATE, true, info.rootNote, info.loopStart, info.loopEnd)
            }
            return convert(WavCodec.decode(bytes, info), info.rootNote, info.loopStart, info.loopEnd)
        }
        if (AiffCodec.isAiff(bytes)) return convert(AiffCodec.decode(bytes))
        val decoder = fallbackDecoder ?: throw UnsupportedAudioException("Unknown audio format")
        return convert(decoder(bytes))
    }

    fun convert(src: PcmAudio, rootNote: Int? = null, loopStart: Long? = null, loopEnd: Long? = null): PreparedSound {
        if (src.sampleRate !in 3000..768000) throw UnsupportedAudioException("Sample rate ${src.sampleRate} Hz out of range (3000–768000)")
        if (src.channels !in 1..2) throw UnsupportedAudioException("${src.channels} channels: only mono and stereo are supported")
        if (src.seconds > MAX_SECONDS) throw UnsupportedAudioException("Audio is %.1f s long; the maximum is %.0f s".format(src.seconds, MAX_SECONDS))

        val resampled = Resampler.resample(src.samples, src.channels, src.sampleRate, TARGET_RATE)
        val ratio = TARGET_RATE.toDouble() / src.sampleRate
        return PreparedSound(
            WavCodec.encodePcm16(resampled), src.channels, TARGET_RATE, false, rootNote,
            loopStart?.let { Math.round(it * ratio) }, loopEnd?.let { Math.round(it * ratio) },
        )
    }
}
