package de.koii.companion.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("koii", Context.MODE_PRIVATE)

    private val _midiChannel = MutableStateFlow(sp.getInt("midi_channel", 1))
    /** 1..16 */
    val midiChannel: StateFlow<Int> = _midiChannel

    private val _velocity = MutableStateFlow(sp.getInt("velocity", 100))
    val velocity: StateFlow<Int> = _velocity

    private val _recordBits = MutableStateFlow(sp.getInt("record_bits", 24))
    /** 16, 24 or 32 (float) */
    val recordBits: StateFlow<Int> = _recordBits

    private val _bpm = MutableStateFlow(sp.getInt("bpm", 120))
    val bpm: StateFlow<Int> = _bpm

    private val _theme = MutableStateFlow(sp.getString("theme", "koii") ?: "koii")
    /** Palette id, see Palettes. */
    val theme: StateFlow<String> = _theme

    private val _loopDir = MutableStateFlow(sp.getString("loop_dir", null))
    /** Tree URI of the optional loop folder on the phone. */
    val loopDir: StateFlow<String?> = _loopDir

    fun setTheme(id: String) { _theme.value = id; sp.edit().putString("theme", id).apply() }
    fun setLoopDir(uri: String?) { _loopDir.value = uri; sp.edit().putString("loop_dir", uri).apply() }

    fun setMidiChannel(v: Int) { _midiChannel.value = v.coerceIn(1, 16); sp.edit().putInt("midi_channel", _midiChannel.value).apply() }
    fun setVelocity(v: Int) { _velocity.value = v.coerceIn(1, 127); sp.edit().putInt("velocity", _velocity.value).apply() }
    fun setRecordBits(v: Int) { _recordBits.value = v; sp.edit().putInt("record_bits", v).apply() }
    fun setBpm(v: Int) { _bpm.value = v.coerceIn(30, 300); sp.edit().putInt("bpm", _bpm.value).apply() }
}
