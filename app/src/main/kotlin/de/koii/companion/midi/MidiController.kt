package de.koii.companion.midi

import de.koii.companion.data.DeviceSession
import de.koii.companion.data.Link
import de.koii.companion.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface MidiEvent {
    data class NoteOn(val note: Int, val velocity: Int) : MidiEvent
    data class NoteOff(val note: Int) : MidiEvent
    data object Start : MidiEvent
    data object Stop : MidiEvent
    data object Continue : MidiEvent
    data object Clock : MidiEvent
}

/** Pad triggering, transport and clock over the same USB-MIDI connection that carries the SysEx traffic. */
class MidiController(
    private val session: DeviceSession,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    companion object {
        const val FIRST_NOTE = 36 // C2
        const val LAST_NOTE = 83  // B5
        const val GROUPS = 4
        const val PADS_PER_GROUP = 12

        /** Group A–D (0–3) × pad 1–12 (0–11) → MIDI note 36–83. */
        fun padNote(group: Int, pad: Int): Int = FIRST_NOTE + group.coerceIn(0, GROUPS - 1) * PADS_PER_GROUP + pad.coerceIn(0, PADS_PER_GROUP - 1)

        fun parse(m: ByteArray): MidiEvent? {
            if (m.isEmpty()) return null
            val s = m[0].toInt() and 0xFF
            return when {
                s == 0xF8 -> MidiEvent.Clock
                s == 0xFA -> MidiEvent.Start
                s == 0xFB -> MidiEvent.Continue
                s == 0xFC -> MidiEvent.Stop
                m.size == 3 && (s and 0xF0) == 0x90 ->
                    if (m[2].toInt() > 0) MidiEvent.NoteOn(m[1].toInt(), m[2].toInt()) else MidiEvent.NoteOff(m[1].toInt())
                m.size == 3 && (s and 0xF0) == 0x80 -> MidiEvent.NoteOff(m[1].toInt())
                else -> null
            }
        }
    }

    private val _events = MutableSharedFlow<MidiEvent>(extraBufferCapacity = 256)
    val events: SharedFlow<MidiEvent> = _events.asSharedFlow()

    private val _held = MutableStateFlow<Set<Int>>(emptySet())
    /** Notes currently held according to incoming Note On/Off (pads pressed on the device). */
    val held: StateFlow<Set<Int>> = _held

    private val _clockRunning = MutableStateFlow(false)
    val clockRunning: StateFlow<Boolean> = _clockRunning

    private var listener: Job? = null
    private var clock: Job? = null

    init {
        scope.launch {
            session.link.collect { link ->
                listener?.cancel()
                stopClock()
                _held.value = emptySet()
                val t = session.transport
                if (link is Link.Online && t != null) {
                    listener = scope.launch {
                        t.incoming.collect { raw ->
                            val e = parse(raw) ?: return@collect
                            when (e) {
                                is MidiEvent.NoteOn -> _held.update { it + e.note }
                                is MidiEvent.NoteOff -> _held.update { it - e.note }
                                else -> Unit
                            }
                            _events.tryEmit(e)
                        }
                    }
                }
            }
        }
    }

    private val channel: Int get() = (prefs.midiChannel.value - 1) and 0x0F

    private suspend fun send(vararg bytes: Int) {
        val t = session.transport ?: return
        t.send(ByteArray(bytes.size) { bytes[it].toByte() })
    }

    suspend fun noteOn(note: Int, velocity: Int = prefs.velocity.value) {
        if (note in FIRST_NOTE..LAST_NOTE) send(0x90 or channel, note, velocity.coerceIn(1, 127))
    }

    suspend fun noteOff(note: Int) {
        if (note in FIRST_NOTE..LAST_NOTE) send(0x80 or channel, note, 0)
    }

    /** Any note 0..127 (keyboard / chords), unlike [noteOn] which is limited to the pad range. */
    suspend fun keyOn(note: Int, velocity: Int = prefs.velocity.value) {
        if (note in 0..127) send(0x90 or channel, note, velocity.coerceIn(1, 127))
    }

    suspend fun keyOff(note: Int) {
        if (note in 0..127) send(0x80 or channel, note, 0)
    }

    /** CC 123 on the current channel. */
    suspend fun allNotesOff() = send(0xB0 or channel, 123, 0)

    suspend fun start() = send(0xFA)
    suspend fun stop() = send(0xFC)
    suspend fun cont() = send(0xFB)

    /** Sends MIDI clock (24 ppqn) at [bpm] until [stopClock]. */
    fun startClock(bpm: Int) {
        stopClock()
        _clockRunning.value = true
        clock = scope.launch {
            val interval = 60_000_000_000.0 / (bpm.coerceIn(30, 300) * 24)
            var next = System.nanoTime().toDouble()
            while (isActive) {
                send(0xF8)
                next += interval
                val wait = ((next - System.nanoTime()) / 1_000_000).toLong()
                if (wait > 0) delay(wait)
            }
        }
    }

    fun stopClock() {
        clock?.cancel()
        clock = null
        _clockRunning.value = false
    }
}
