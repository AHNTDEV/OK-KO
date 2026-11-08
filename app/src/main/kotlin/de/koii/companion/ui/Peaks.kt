package de.koii.companion.ui

import kotlin.math.abs

object Peaks {
    /** Peak per bucket (0..1) of s16le PCM, all channels folded together. */
    fun fromPcm16(pcm: ByteArray, channels: Int, buckets: Int): FloatArray {
        val frames = pcm.size / (2 * channels)
        if (frames == 0 || buckets <= 0) return FloatArray(0)
        val n = minOf(buckets, frames)
        val out = FloatArray(n)
        for (f in 0 until frames) {
            val b = (f.toLong() * n / frames).toInt()
            for (c in 0 until channels) {
                val o = (f * channels + c) * 2
                val s = ((pcm[o].toInt() and 0xFF) or (pcm[o + 1].toInt() shl 8)).toShort()
                val v = abs(s.toInt()) / 32768f
                if (v > out[b]) out[b] = v
            }
        }
        return out
    }

    fun fromFloats(samples: FloatArray, channels: Int, buckets: Int): FloatArray {
        val frames = samples.size / channels
        if (frames == 0 || buckets <= 0) return FloatArray(0)
        val n = minOf(buckets, frames)
        val out = FloatArray(n)
        for (f in 0 until frames) {
            val b = (f.toLong() * n / frames).toInt()
            for (c in 0 until channels) {
                val v = abs(samples[f * channels + c])
                if (v > out[b]) out[b] = v
            }
        }
        return out
    }
}
