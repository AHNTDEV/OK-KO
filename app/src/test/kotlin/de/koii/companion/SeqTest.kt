package de.koii.companion

import de.koii.companion.seq.MidiFile
import de.koii.companion.seq.Pattern
import de.koii.companion.seq.SequencerEngine
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SeqTest {
    private val kick = Pattern.track(0, 0)   // note 36
    private val snare = Pattern.track(0, 3)  // note 39

    private fun demo(): Pattern = Pattern.empty("Demo").withBpm(96)
        .withCell(kick, 0, 100).withCell(kick, 4, 100).withCell(kick, 8, 127).withCell(kick, 12, 100)
        .withCell(snare, 4, 100).withCell(snare, 12, 127)

    @Test
    fun cellsCycleOffNormalAccent() {
        var p = Pattern.empty()
        p = p.cycle(0, 0); assertEquals(Pattern.VEL_NORMAL, p.cell(0, 0))
        p = p.cycle(0, 0); assertEquals(Pattern.VEL_ACCENT, p.cell(0, 0))
        p = p.cycle(0, 0); assertEquals(0, p.cell(0, 0))
    }

    @Test
    fun notesAtListsNotesWithVelocity() {
        assertEquals(listOf(36 to 100), demo().notesAt(0))
        assertEquals(listOf(36 to 100, 39 to 100), demo().notesAt(4))
        assertTrue(demo().notesAt(1).isEmpty())
    }

    @Test
    fun trackMappingCoversAllGroups() {
        assertEquals(0, Pattern.track(0, 0))
        assertEquals(47, Pattern.track(3, 11))
        assertEquals(83, Pattern.noteOf(47))
    }

    @Test
    fun outOfRangeEditsAreIgnored() {
        val p = Pattern.empty()
        assertEquals(p, p.withCell(99, 0, 100))
        assertEquals(p, p.withCell(0, 99, 100))
    }

    @Test
    fun hitCountOnlyCountsWithinLength() {
        val p = demo()
        assertEquals(6, p.hitCount())
        assertEquals(3, p.withLength(8).hitCount())
    }

    @Test
    fun midiFileRoundTrip() {
        val original = demo()
        val back = MidiFile.read(MidiFile.write(original, 0)).pattern
        assertEquals(original.bpm, back.bpm)
        assertEquals(16, back.length)
        for (t in 0 until Pattern.TRACKS) for (s in 0 until 16) {
            assertEquals(original.cell(t, s) > 0, back.cell(t, s) > 0, "track $t step $s")
        }
        assertEquals(Pattern.VEL_ACCENT, back.cell(kick, 8))
        assertEquals(Pattern.VEL_NORMAL, back.cell(kick, 0))
    }

    @Test
    fun midiFileHasValidHeader() {
        val b = MidiFile.write(demo())
        assertEquals("MThd", String(b, 0, 4))
        assertEquals("MTrk", String(b, 14, 4))
    }

    @Test
    fun midiImportSkipsNotesOutsidePadRange() {
        val p = Pattern.empty().withCell(0, 0, 100)
        val bytes = MidiFile.write(p)
        // append nothing; instead build a file with a note below 36 by patching the first note number
        val patched = bytes.copyOf()
        for (i in 0 until patched.size - 2) {
            if (patched[i] == 0x90.toByte() && patched[i + 1].toInt() == 36) { patched[i + 1] = 20; break }
        }
        val r = MidiFile.read(patched)
        assertEquals(1, r.skippedNotes)
        assertEquals(0, r.pattern.hitCount())
    }

    @Test
    fun midiImportRejectsGarbage() {
        assertFailsWith<IllegalArgumentException> { MidiFile.read(ByteArray(20) { 1 }) }
    }

    @Test
    fun jsonRoundTrip() {
        val json = Json { encodeDefaults = true }
        val p = demo().withSwing(30)
        assertEquals(p, json.decodeFromString<Pattern>(json.encodeToString(p)))
    }

    @Test
    fun stepTimingFollowsTempo() {
        assertEquals(125_000_000L, SequencerEngine.stepNanos(120))
        assertEquals(0L, SequencerEngine.swingNanos(0, 50, 125_000_000L))
        assertEquals(62_500_000L, SequencerEngine.swingNanos(1, 50, 125_000_000L))
    }
}
