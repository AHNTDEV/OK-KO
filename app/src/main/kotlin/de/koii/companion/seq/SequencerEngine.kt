package de.koii.companion.seq

import de.koii.companion.midi.MidiController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SeqState(val running: Boolean = false, val step: Int = -1)

/** Plays a [Pattern] as pad notes over MIDI. Tempo, swing and cells are read on every step, so edits apply live. */
class SequencerEngine(private val midi: MidiController, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(SeqState())
    val state: StateFlow<SeqState> = _state

    private var job: Job? = null

    val running: Boolean get() = job?.isActive == true

    companion object {
        /** Duration of one step in nanoseconds for [bpm] (sixteenth notes). */
        fun stepNanos(bpm: Int): Long = 15_000_000_000L / bpm.coerceIn(30, 300)

        /** Extra delay of step [step] in nanoseconds: every second step is pushed back by [swing] percent. */
        fun swingNanos(step: Int, swing: Int, stepNanos: Long): Long =
            if (step % 2 == 1) stepNanos * swing.coerceIn(0, 60) / 100 else 0L
    }

    /**
     * @param source current pattern, called once per step
     * @param sendClock also send MIDI start and 24 ppqn clock, so the K.O. II follows the tempo
     */
    fun start(source: () -> Pattern, sendClock: Boolean) {
        stop()
        job = scope.launch {
            val clock = if (sendClock) launch { runClock(source) } else null
            if (sendClock) midi.start()
            var next = System.nanoTime()
            var i = 0L
            try {
                while (isActive) {
                    val p = source()
                    val step = (i % p.length).toInt()
                    val stepNs = stepNanos(p.bpm)
                    sleepUntil(next + swingNanos(step, p.swing, stepNs))
                    _state.value = SeqState(true, step)
                    val gateMs = minOf(stepNs / 2_000_000L, 120L)
                    for ((note, vel) in p.notesAt(step)) {
                        midi.noteOn(note, vel)
                        launch { delay(gateMs); midi.noteOff(note) }
                    }
                    next += stepNs
                    i++
                }
            } finally {
                clock?.cancel()
                withContext(NonCancellable) { if (sendClock) midi.stop() }
                _state.value = SeqState()
            }
        }
    }

    private suspend fun runClock(source: () -> Pattern) {
        var next = System.nanoTime()
        while (true) {
            midi.sendClockTick()
            next += stepNanos(source().bpm) / 6
            sleepUntil(next)
        }
    }

    private suspend fun sleepUntil(t: Long) {
        val ms = (t - System.nanoTime()) / 1_000_000L
        if (ms > 0) delay(ms)
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
