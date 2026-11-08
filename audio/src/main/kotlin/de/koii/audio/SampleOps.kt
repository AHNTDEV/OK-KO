package de.koii.audio

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** A frame range [start, end) of a sample. */
data class FrameRange(val start: Int, val end: Int) {
    val length: Int get() = end - start
    fun isEmpty() = end <= start
}

/**
 * Pure editing operations. Every operation returns a new [PcmAudio]; the input is never changed, which keeps
 * undo trivial. Ranges are in frames and clamped to the sample.
 */
object SampleOps {
    private fun clamp(a: PcmAudio, r: FrameRange?): FrameRange {
        val start = (r?.start ?: 0).coerceIn(0, a.frames)
        val end = (r?.end ?: a.frames).coerceIn(start, a.frames)
        return FrameRange(start, end)
    }

    private inline fun mapRange(a: PcmAudio, r: FrameRange?, f: (frameInRange: Int, rangeLength: Int, ch: Int, v: Float) -> Float): PcmAudio {
        val rr = clamp(a, r)
        val out = a.samples.copyOf()
        for (fr in rr.start until rr.end) {
            for (c in 0 until a.channels) {
                val i = fr * a.channels + c
                out[i] = f(fr - rr.start, rr.length, c, out[i])
            }
        }
        return PcmAudio(a.sampleRate, a.channels, out)
    }

    /** Keeps only [range]. */
    fun crop(a: PcmAudio, range: FrameRange): PcmAudio {
        val r = clamp(a, range)
        return PcmAudio(a.sampleRate, a.channels, a.samples.copyOfRange(r.start * a.channels, r.end * a.channels))
    }

    /** Removes [range] and joins the rest. */
    fun cut(a: PcmAudio, range: FrameRange): PcmAudio {
        val r = clamp(a, range)
        val head = a.samples.copyOfRange(0, r.start * a.channels)
        val tail = a.samples.copyOfRange(r.end * a.channels, a.samples.size)
        return PcmAudio(a.sampleRate, a.channels, head + tail)
    }

    fun silence(a: PcmAudio, range: FrameRange): PcmAudio = mapRange(a, range) { _, _, _, _ -> 0f }

    fun reverse(a: PcmAudio, range: FrameRange? = null): PcmAudio {
        val r = clamp(a, range)
        val out = a.samples.copyOf()
        for (i in 0 until r.length) {
            for (c in 0 until a.channels) {
                out[(r.start + i) * a.channels + c] = a.samples[(r.end - 1 - i) * a.channels + c]
            }
        }
        return PcmAudio(a.sampleRate, a.channels, out)
    }

    fun gainDb(a: PcmAudio, db: Float, range: FrameRange? = null): PcmAudio {
        val g = 10f.pow(db / 20f)
        return mapRange(a, range) { _, _, _, v -> v * g }
    }

    /** Fades from silence to full level across [range] (whole sample if null). */
    fun fadeIn(a: PcmAudio, range: FrameRange? = null): PcmAudio =
        mapRange(a, range) { i, n, _, v -> v * (i.toFloat() / max(1, n - 1)) }

    fun fadeOut(a: PcmAudio, range: FrameRange? = null): PcmAudio =
        mapRange(a, range) { i, n, _, v -> v * (1f - i.toFloat() / max(1, n - 1)) }

    fun peak(a: PcmAudio, range: FrameRange? = null): Float {
        val r = clamp(a, range)
        var p = 0f
        for (i in r.start * a.channels until r.end * a.channels) p = max(p, abs(a.samples[i]))
        return p
    }

    /** Scales the whole sample so its loudest value reaches [target] (default about -0.2 dBFS). Silence stays silent. */
    fun normalize(a: PcmAudio, target: Float = 0.98f): PcmAudio {
        val p = peak(a)
        if (p <= 1e-6f) return a
        val g = target / p
        return PcmAudio(a.sampleRate, a.channels, FloatArray(a.samples.size) { a.samples[it] * g })
    }

    fun toMono(a: PcmAudio): PcmAudio {
        if (a.channels == 1) return a
        val out = FloatArray(a.frames)
        for (f in 0 until a.frames) {
            var sum = 0f
            for (c in 0 until a.channels) sum += a.samples[f * a.channels + c]
            out[f] = sum / a.channels
        }
        return PcmAudio(a.sampleRate, 1, out)
    }

    /** Removes silence below [threshold] at the start and end. Returns the input if everything is silent. */
    fun trimSilence(a: PcmAudio, threshold: Float = 0.01f): PcmAudio {
        var first = -1
        var last = -1
        for (f in 0 until a.frames) {
            var loud = false
            for (c in 0 until a.channels) if (abs(a.samples[f * a.channels + c]) > threshold) { loud = true; break }
            if (loud) { if (first < 0) first = f; last = f }
        }
        if (first < 0) return a
        return crop(a, FrameRange(first, last + 1))
    }

    /**
     * Repitches by [semitones] the way a sampler does: the sample plays faster or slower, so the length changes.
     * +12 halves the length, -12 doubles it.
     */
    fun repitch(a: PcmAudio, semitones: Double): PcmAudio {
        if (semitones == 0.0 || a.frames == 0) return a
        val factor = 2.0.pow(semitones / 12.0)
        val fakeIn = max(1, (a.sampleRate * factor).roundToInt())
        return PcmAudio(a.sampleRate, a.channels, Resampler.resample(a.samples, a.channels, fakeIn, a.sampleRate))
    }

    fun durationSeconds(a: PcmAudio): Double = a.seconds

    /** Frame range from fractions 0..1 of the sample length; ordered and at least one frame wide when possible. */
    fun rangeFromFractions(a: PcmAudio, from: Float, to: Float): FrameRange {
        val lo = min(from, to).coerceIn(0f, 1f)
        val hi = max(from, to).coerceIn(0f, 1f)
        val s = (lo * a.frames).roundToInt().coerceIn(0, a.frames)
        val e = (hi * a.frames).roundToInt().coerceIn(s, a.frames)
        return FrameRange(s, e)
    }
}
