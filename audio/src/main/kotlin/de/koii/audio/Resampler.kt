package de.koii.audio

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Windowed-sinc (Blackman-Harris-like Hann) resampler for interleaved float audio. */
object Resampler {
    fun resample(input: FloatArray, channels: Int, inRate: Int, outRate: Int, halfTaps: Int = 16): FloatArray {
        if (inRate == outRate) return input
        val inFrames = input.size / channels
        if (inFrames == 0) return FloatArray(0)
        val outFrames = ceil(inFrames.toDouble() * outRate / inRate).toInt()
        val step = inRate.toDouble() / outRate
        val cutoff = min(1.0, outRate.toDouble() / inRate)   // fraction of the input Nyquist
        val width = halfTaps / cutoff
        val out = FloatArray(outFrames * channels)
        val weights = DoubleArray(2 * ceil(width).toInt() + 2)

        for (n in 0 until outFrames) {
            val center = n * step
            val first = max(0, floor(center - width).toInt() + 1)
            val last = min(inFrames - 1, floor(center + width).toInt())
            var sum = 0.0
            for (k in first..last) {
                val x = k - center
                val w = cutoff * sinc(cutoff * x) * hann(x / width)
                weights[k - first] = w
                sum += w
            }
            if (sum == 0.0) continue
            for (c in 0 until channels) {
                var acc = 0.0
                for (k in first..last) acc += weights[k - first] * input[k * channels + c]
                out[n * channels + c] = (acc / sum).toFloat()
            }
        }
        return out
    }

    private fun sinc(x: Double): Double = if (x == 0.0) 1.0 else sin(PI * x) / (PI * x)
    private fun hann(t: Double): Double = if (t <= -1.0 || t >= 1.0) 0.0 else 0.5 * (1.0 + cos(PI * t))
}
