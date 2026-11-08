package de.koii.audio

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SampleOpsTest {
    private fun mono(vararg v: Float) = PcmAudio(46875, 1, floatArrayOf(*v))

    @Test
    fun cropKeepsRange() {
        val r = SampleOps.crop(mono(0f, 0.1f, 0.2f, 0.3f, 0.4f), FrameRange(1, 4))
        assertContentEquals(floatArrayOf(0.1f, 0.2f, 0.3f), r.samples)
    }

    @Test
    fun cropIsClamped() {
        assertEquals(5, SampleOps.crop(mono(0f, 0f, 0f, 0f, 0f), FrameRange(-4, 99)).frames)
    }

    @Test
    fun cutRemovesRangeAndJoins() {
        val r = SampleOps.cut(mono(1f, 2f, 3f, 4f, 5f), FrameRange(1, 3))
        assertContentEquals(floatArrayOf(1f, 4f, 5f), r.samples)
    }

    @Test
    fun silenceZeroesOnlyRange() {
        val r = SampleOps.silence(mono(1f, 1f, 1f, 1f), FrameRange(1, 3))
        assertContentEquals(floatArrayOf(1f, 0f, 0f, 1f), r.samples)
    }

    @Test
    fun reverseWholeAndRange() {
        assertContentEquals(floatArrayOf(3f, 2f, 1f), SampleOps.reverse(mono(1f, 2f, 3f)).samples)
        assertContentEquals(floatArrayOf(1f, 3f, 2f, 4f), SampleOps.reverse(mono(1f, 2f, 3f, 4f), FrameRange(1, 3)).samples)
    }

    @Test
    fun reverseKeepsStereoPairsTogether() {
        val st = PcmAudio(46875, 2, floatArrayOf(1f, -1f, 2f, -2f, 3f, -3f))
        assertContentEquals(floatArrayOf(3f, -3f, 2f, -2f, 1f, -1f), SampleOps.reverse(st).samples)
    }

    @Test
    fun gainSixDbDoublesAmplitude() {
        val r = SampleOps.gainDb(mono(0.25f), 6.0206f)
        assertEquals(0.5f, r.samples[0], 1e-3f)
    }

    @Test
    fun normalizeHitsTarget() {
        val r = SampleOps.normalize(mono(0.1f, -0.25f, 0.05f), 0.98f)
        assertEquals(0.98f, SampleOps.peak(r), 1e-4f)
        assertTrue(r.samples[1] < 0)
    }

    @Test
    fun normalizeLeavesSilence() {
        val s = mono(0f, 0f)
        assertContentEquals(s.samples, SampleOps.normalize(s).samples)
    }

    @Test
    fun fadesGoToZeroAndFromZero() {
        val a = mono(1f, 1f, 1f, 1f, 1f)
        val fi = SampleOps.fadeIn(a)
        assertEquals(0f, fi.samples.first(), 1e-6f)
        assertEquals(1f, fi.samples.last(), 1e-6f)
        val fo = SampleOps.fadeOut(a)
        assertEquals(1f, fo.samples.first(), 1e-6f)
        assertEquals(0f, fo.samples.last(), 1e-6f)
    }

    @Test
    fun monoMixAveragesChannels() {
        val r = SampleOps.toMono(PcmAudio(46875, 2, floatArrayOf(1f, 0f, 0.5f, 0.5f)))
        assertEquals(1, r.channels)
        assertContentEquals(floatArrayOf(0.5f, 0.5f), r.samples)
    }

    @Test
    fun trimSilenceCutsBothEnds() {
        val r = SampleOps.trimSilence(mono(0f, 0f, 0.5f, 0.4f, 0f, 0f, 0f))
        assertContentEquals(floatArrayOf(0.5f, 0.4f), r.samples)
    }

    @Test
    fun repitchChangesLengthByOctave() {
        val a = PcmAudio(46875, 1, FloatArray(4000) { (it % 50) / 50f })
        assertTrue(abs(SampleOps.repitch(a, 12.0).frames - 2000) <= 2)
        assertTrue(abs(SampleOps.repitch(a, -12.0).frames - 8000) <= 2)
        assertEquals(a.frames, SampleOps.repitch(a, 0.0).frames)
    }

    @Test
    fun rangeFromFractionsIsOrdered() {
        val a = PcmAudio(46875, 1, FloatArray(1000))
        assertEquals(FrameRange(250, 750), SampleOps.rangeFromFractions(a, 0.75f, 0.25f))
        assertEquals(FrameRange(0, 1000), SampleOps.rangeFromFractions(a, -1f, 2f))
    }

    @Test
    fun opsDoNotMutateInput() {
        val a = mono(1f, 2f, 3f)
        SampleOps.reverse(a); SampleOps.gainDb(a, 6f); SampleOps.silence(a, FrameRange(0, 3))
        assertContentEquals(floatArrayOf(1f, 2f, 3f), a.samples)
    }
}
