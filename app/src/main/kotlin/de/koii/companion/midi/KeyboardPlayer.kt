package de.koii.companion.midi

import de.koii.companion.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KeysConfig(
    val quality: Quality? = Quality.MAJOR,
    val extension: Extension? = null,
    val lowerOctave: Boolean = false,
    val arp: Boolean = false,
    val arpMode: ArpMode = ArpMode.UP,
    val arpRate: ArpRate = ArpRate.SIXTEENTH,
    val arpOctaves: Int = 1,
    /** Octave of the lowest key on screen, in MIDI octave numbers (4 → C4 = 60). */
    val octave: Int = 3,
)

/** Plays chords or arpeggios from on-screen keys. Several keys can sound at once. */
class KeyboardPlayer(
    private val midi: MidiController,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {
    private val _config = MutableStateFlow(KeysConfig())
    val config: StateFlow<KeysConfig> = _config

    private val _down = MutableStateFlow<Set<Int>>(emptySet())
    /** Root notes of keys currently held. */
    val down: StateFlow<Set<Int>> = _down

    private val voices = HashMap<Int, Job>()

    fun update(f: (KeysConfig) -> KeysConfig) = _config.update(f)

    fun press(root: Int, velocity: Int = prefs.velocity.value) {
        release(root)
        _down.update { it + root }
        val c = _config.value
        voices[root] = scope.launch {
            val notes = Chords.notes(root, c.quality, c.extension, c.lowerOctave)
            if (!c.arp) {
                try {
                    notes.forEach { midi.keyOn(it, velocity) }
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { notes.forEach { midi.keyOff(it) } }
                }
            } else {
                var i = 0
                var seq = Arp.sequence(notes, c.arpMode, c.arpOctaves)
                var sounding = -1
                try {
                    while (isActive && seq.isNotEmpty()) {
                        if (i % seq.size == 0 && c.arpMode == ArpMode.RANDOM) seq = Arp.sequence(notes, c.arpMode, c.arpOctaves)
                        val n = seq[i % seq.size]
                        val step = Arp.stepMillis(prefs.bpm.value, c.arpRate)
                        midi.keyOn(n, velocity)
                        sounding = n
                        delay(step * 8 / 10)
                        midi.keyOff(n)
                        sounding = -1
                        delay(step - step * 8 / 10)
                        i++
                    }
                } finally {
                    if (sounding >= 0) withContext(NonCancellable) { midi.keyOff(sounding) }
                }
            }
        }
    }

    fun release(root: Int) {
        voices.remove(root)?.cancel()
        _down.update { it - root }
    }

    fun releaseAll() {
        voices.keys.toList().forEach(::release)
    }

    private suspend fun awaitCancellation(): Nothing = kotlinx.coroutines.awaitCancellation()
}
