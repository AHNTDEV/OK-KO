package de.koii.companion.seq

import kotlinx.serialization.Serializable

/**
 * A step pattern over all 48 pads (groups A–D, notes 36–83). One step is a sixteenth note.
 * A cell holds a velocity: 0 is off, [VEL_NORMAL] a normal hit, [VEL_ACCENT] an accent.
 */
@Serializable
data class Pattern(
    val name: String = "Pattern",
    val length: Int = 16,
    val bpm: Int = 120,
    /** Delay of every second step as a percentage of a step, 0..60. */
    val swing: Int = 0,
    /** [TRACKS] lists of [MAX_STEPS] velocities each; track = note - [FIRST_NOTE]. */
    val steps: List<List<Int>> = List(TRACKS) { List(MAX_STEPS) { 0 } },
) {
    companion object {
        const val TRACKS = 48
        const val MAX_STEPS = 32
        const val FIRST_NOTE = 36
        const val VEL_NORMAL = 100
        const val VEL_ACCENT = 127
        val LENGTHS = listOf(8, 16, 24, 32)

        fun empty(name: String = "Pattern") = Pattern(name = name)

        fun track(group: Int, pad: Int): Int = group.coerceIn(0, 3) * 12 + pad.coerceIn(0, 11)
        fun noteOf(track: Int): Int = FIRST_NOTE + track
    }

    fun cell(track: Int, step: Int): Int = steps.getOrNull(track)?.getOrNull(step) ?: 0

    fun withCell(track: Int, step: Int, velocity: Int): Pattern {
        if (track !in 0 until TRACKS || step !in 0 until MAX_STEPS) return this
        val row = steps[track].toMutableList().also { it[step] = velocity.coerceIn(0, 127) }
        return copy(steps = steps.toMutableList().also { it[track] = row })
    }

    /** Off → normal → accent → off. */
    fun cycle(track: Int, step: Int): Pattern = withCell(
        track, step,
        when (cell(track, step)) { 0 -> VEL_NORMAL; VEL_NORMAL -> VEL_ACCENT; else -> 0 },
    )

    fun withLength(n: Int): Pattern = copy(length = n.coerceIn(1, MAX_STEPS))
    fun withBpm(v: Int): Pattern = copy(bpm = v.coerceIn(30, 300))
    fun withSwing(v: Int): Pattern = copy(swing = v.coerceIn(0, 60))
    fun cleared(): Pattern = copy(steps = List(TRACKS) { List(MAX_STEPS) { 0 } })

    /** Clears one track (one pad) only. */
    fun withoutTrack(track: Int): Pattern =
        if (track !in 0 until TRACKS) this else copy(steps = steps.toMutableList().also { it[track] = List(MAX_STEPS) { 0 } })

    /** (note, velocity) pairs that sound on [step]. */
    fun notesAt(step: Int): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        for (t in 0 until TRACKS) {
            val v = cell(t, step)
            if (v > 0) out += noteOf(t) to v
        }
        return out
    }

    fun hitCount(): Int {
        var n = 0
        for (t in 0 until TRACKS) for (s in 0 until length) if (cell(t, s) > 0) n++
        return n
    }

    /** Steps beyond [length] are ignored when playing; this drops them from the data too. */
    fun trimmed(): Pattern = copy(steps = steps.map { row -> row.mapIndexed { i, v -> if (i < length) v else 0 } })
}
