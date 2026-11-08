package de.koii.companion.midi

import kotlin.random.Random

enum class Quality(val label: String, val intervals: List<Int>) {
    MAJOR("Maj", listOf(0, 4, 7)),
    MINOR("Min", listOf(0, 3, 7)),
    SUS("Sus", listOf(0, 5, 7)),
    DIM("Dim", listOf(0, 3, 6)),
}

/** Added on top of a triad: 6th, 7th (minor 7th) or 9th. */
enum class Extension(val label: String, val interval: Int) {
    SIXTH("6", 9),
    SEVENTH("7", 10),
    NINTH("9", 14),
}

object Chords {
    private val NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun noteName(note: Int): String = NAMES[((note % 12) + 12) % 12] + (note / 12 - 1)

    /** Notes of the chord for [root]; no [quality] means the single note. Result is sorted and within 0..127. */
    fun notes(root: Int, quality: Quality?, extension: Extension?, lowerOctave: Boolean): List<Int> {
        val out = sortedSetOf<Int>()
        if (quality == null) {
            out += root
        } else {
            quality.intervals.forEach { out += root + it }
            extension?.let { out += root + it.interval }
        }
        if (lowerOctave) out += root - 12
        return out.filter { it in 0..127 }
    }
}

enum class ArpMode(val label: String) { UP("Up"), DOWN("Down"), UP_DOWN("Up/Dn"), RANDOM("Rnd") }

/** Steps per quarter note. */
enum class ArpRate(val label: String, val perBeat: Double) {
    QUARTER("1/4", 1.0),
    EIGHTH("1/8", 2.0),
    EIGHTH_T("1/8T", 3.0),
    SIXTEENTH("1/16", 4.0),
    SIXTEENTH_T("1/16T", 6.0),
    THIRTYSECOND("1/32", 8.0),
}

object Arp {
    /** Note order for one pass of the arpeggio over [octaves] octaves (1..3). */
    fun sequence(notes: List<Int>, mode: ArpMode, octaves: Int, random: Random = Random.Default): List<Int> {
        if (notes.isEmpty()) return emptyList()
        val base = notes.sorted()
        val up = (0 until octaves.coerceIn(1, 3)).flatMap { o -> base.map { it + 12 * o } }.filter { it in 0..127 }
        return when (mode) {
            ArpMode.UP -> up
            ArpMode.DOWN -> up.reversed()
            ArpMode.UP_DOWN -> if (up.size <= 2) up else up + up.subList(1, up.size - 1).reversed()
            ArpMode.RANDOM -> up.shuffled(random)
        }
    }

    fun stepMillis(bpm: Int, rate: ArpRate): Long = (60_000.0 / (bpm.coerceIn(30, 300) * rate.perBeat)).toLong()
}
