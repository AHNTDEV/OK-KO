package de.koii.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AudioConverterTest {
    private fun sine(rate: Int, seconds: Double, hz: Double, channels: Int = 1): FloatArray {
        val n = (rate * seconds).toInt()
        return FloatArray(n * channels) { i -> (0.5 * sin(2 * PI * hz * (i / channels) / rate)).toFloat() }
    }

    private fun wav(samples: FloatArray, rate: Int, channels: Int) =
        WavCodec.wrapPcm16(WavCodec.encodePcm16(samples), channels, rate)

    @Test
    fun fastPathSendsDataChunkUntouched() {
        val pcm = WavCodec.encodePcm16(sine(46875, 0.1, 440.0))
        val file = WavCodec.wrapPcm16(pcm, 1, 46875)
        val p = AudioConverter.prepare(file)
        assertTrue(p.fastPath)
        assertContentEquals(pcm, p.pcm)
        assertEquals(46875, p.sampleRate)
    }

    @Test
    fun resamples44100To46875() {
        val src = sine(44100, 1.0, 1000.0)
        val p = AudioConverter.prepare(wav(src, 44100, 1))
        assertTrue(!p.fastPath)
        assertEquals(46875, p.sampleRate)
        assertEquals(46875, p.frames)
        // amplitude survives and the tone is still 1 kHz: check peak and zero crossings in the middle
        val out = FloatArray(p.frames) { i -> (p.pcm[i * 2].toInt() and 0xFF or (p.pcm[i * 2 + 1].toInt() shl 8)).toShort() / 32768f }
        val peak = out.slice(5000..40000).maxOf { abs(it) }
        assertTrue(peak in 0.47f..0.53f, "peak=$peak")
        var crossings = 0
        for (i in 5001..40000) if (out[i - 1] < 0 && out[i] >= 0) crossings++
        val hz = crossings * 46875.0 / 35000.0
        assertTrue(hz in 980.0..1020.0, "hz=$hz")
    }

    @Test
    fun stereoIsKept() {
        val p = AudioConverter.prepare(wav(sine(48000, 0.2, 500.0, 2), 48000, 2))
        assertEquals(2, p.channels)
    }

    @Test
    fun rejectsTooLong() {
        val file = wav(FloatArray(48000 * 41), 48000, 1)
        assertFailsWith<UnsupportedAudioException> { AudioConverter.prepare(file) }
    }

    @Test
    fun rejectsMoreThanTwoChannels() {
        val src = PcmAudio(48000, 3, FloatArray(3000))
        assertFailsWith<UnsupportedAudioException> { AudioConverter.convert(src) }
    }

    @Test
    fun unknownFormatNeedsFallback() {
        assertFailsWith<UnsupportedAudioException> { AudioConverter.prepare(ByteArray(100)) }
    }

    @Test
    fun decodes24BitWav() {
        val rate = 46875
        val body = ByteArray(6)
        // +0.5 and -0.5 as 24-bit LE
        val pos = 0x400000; val neg = -0x400000
        body[0] = pos.toByte(); body[1] = (pos shr 8).toByte(); body[2] = (pos shr 16).toByte()
        body[3] = neg.toByte(); body[4] = (neg shr 8).toByte(); body[5] = (neg shr 16).toByte()
        val head = WavCodec.wrapPcm16(ByteArray(0), 1, rate).copyOf(44)
        head[20] = 1; head[34] = 24; head[40] = 6
        val data = head + body
        val info = WavCodec.parse(data)
        val pcm = WavCodec.decode(data, info)
        assertEquals(2, pcm.frames)
        assertEquals(0.5f, pcm.samples[0], 1e-4f)
        assertEquals(-0.5f, pcm.samples[1], 1e-4f)
    }

    @Test
    fun smplChunkGivesRootNoteAndLoop() {
        val file = WavCodec.wrapPcm16(WavCodec.encodePcm16(FloatArray(100)), 1, 46875, rootNote = 60, loopStart = 10, loopEnd = 90)
        val info = WavCodec.parse(file)
        assertEquals(60, info.rootNote)
        assertEquals(10L, info.loopStart)
        assertEquals(90L, info.loopEnd)
    }

    @Test
    fun aiffBigEndian16Bit() {
        val rate = 44100
        // 80-bit extended 44100: exp 0x400E, mantissa 0xAC44000000000000
        val ext = byteArrayOf(0x40, 0x0E, 0xAC.toByte(), 0x44, 0, 0, 0, 0, 0, 0)
        val comm = byteArrayOf(0, 1, 0, 0, 0, 2, 0, 16) + ext
        val ssnd = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0x40, 0, 0xC0.toByte(), 0)
        fun chunk(id: String, body: ByteArray) =
            id.toByteArray() + byteArrayOf(0, 0, 0, body.size.toByte()) + body
        val body = "AIFF".toByteArray() + chunk("COMM", comm) + chunk("SSND", ssnd)
        val file = "FORM".toByteArray() + byteArrayOf(0, 0, 0, body.size.toByte()) + body
        val pcm = AiffCodec.decode(file)
        assertEquals(rate, pcm.sampleRate)
        assertEquals(2, pcm.frames)
        assertEquals(0.5f, pcm.samples[0], 1e-4f)
        assertEquals(-0.5f, pcm.samples[1], 1e-4f)
    }
}
