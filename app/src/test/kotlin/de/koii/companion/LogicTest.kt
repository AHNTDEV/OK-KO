package de.koii.companion

import de.koii.companion.midi.Arp
import de.koii.companion.midi.ArpMode
import de.koii.companion.midi.ArpRate
import de.koii.companion.midi.Chords
import de.koii.companion.midi.Extension
import de.koii.companion.midi.MidiController
import de.koii.companion.midi.Quality
import de.koii.companion.ui.Peaks
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LogicTest {
    @Test fun majorTriad() = assertEquals(listOf(60, 64, 67), Chords.notes(60, Quality.MAJOR, null, false))
    @Test fun minorSeventh() = assertEquals(listOf(60, 63, 67, 70), Chords.notes(60, Quality.MINOR, Extension.SEVENTH, false))
    @Test fun susNinth() = assertEquals(listOf(60, 65, 67, 74), Chords.notes(60, Quality.SUS, Extension.NINTH, false))
    @Test fun dimWithLowOctave() = assertEquals(listOf(48, 60, 63, 66), Chords.notes(60, Quality.DIM, null, true))
    @Test fun singleNoteWithoutQuality() = assertEquals(listOf(60), Chords.notes(60, null, Extension.SEVENTH, false))
    @Test fun chordIsClampedToMidiRange() = assertTrue(Chords.notes(125, Quality.MAJOR, null, false).all { it <= 127 })
    @Test fun noteNames() { assertEquals("C4", Chords.noteName(60)); assertEquals("A#3", Chords.noteName(58)) }

    @Test fun arpUpAcrossOctaves() =
        assertEquals(listOf(60, 64, 67, 72, 76, 79), Arp.sequence(listOf(67, 60, 64), ArpMode.UP, 2))

    @Test fun arpDown() = assertEquals(listOf(67, 64, 60), Arp.sequence(listOf(60, 64, 67), ArpMode.DOWN, 1))

    @Test fun arpUpDownDoesNotRepeatTurningPoints() =
        assertEquals(listOf(60, 64, 67, 64), Arp.sequence(listOf(60, 64, 67), ArpMode.UP_DOWN, 1))

    @Test fun arpRandomKeepsAllNotes() =
        assertEquals(listOf(60, 64, 67), Arp.sequence(listOf(60, 64, 67), ArpMode.RANDOM, 1, Random(1)).sorted())

    @Test fun arpStepTiming() {
        assertEquals(500, Arp.stepMillis(120, ArpRate.QUARTER))
        assertEquals(125, Arp.stepMillis(120, ArpRate.SIXTEENTH))
    }

    @Test fun padNotesCoverC2ToB5() {
        assertEquals(36, MidiController.padNote(0, 0))
        assertEquals(83, MidiController.padNote(3, 11))
        assertEquals(60, MidiController.padNote(2, 0))
    }

    @Test
    fun midiParse() {
        assertEquals(
            de.koii.companion.midi.MidiEvent.NoteOn(36, 100),
            MidiController.parse(byteArrayOf(0x90.toByte(), 36, 100)),
        )
        assertEquals(de.koii.companion.midi.MidiEvent.NoteOff(36), MidiController.parse(byteArrayOf(0x90.toByte(), 36, 0)))
        assertEquals(de.koii.companion.midi.MidiEvent.Start, MidiController.parse(byteArrayOf(0xFA.toByte())))
    }

    @Test
    fun peaksFromPcm() {
        // 4 frames mono: 0, +16384, -32768, 0 → 2 buckets
        val pcm = byteArrayOf(0, 0, 0, 0x40, 0, 0x80.toByte(), 0, 0)
        val p = Peaks.fromPcm16(pcm, 1, 2)
        assertEquals(2, p.size)
        assertEquals(0.5f, p[0], 1e-3f)
        assertEquals(1.0f, p[1], 1e-3f)
    }

    @Test
    fun peaksHandleEmptyAndShort() {
        assertContentEquals(FloatArray(0), Peaks.fromPcm16(ByteArray(0), 1, 10))
        assertEquals(1, Peaks.fromFloats(floatArrayOf(0.7f), 1, 50).size)
    }
}
