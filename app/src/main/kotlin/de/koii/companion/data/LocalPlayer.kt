package de.koii.companion.data

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Plays a loop on the phone's speaker so it can be auditioned before it goes to a pad. One at a time. */
class LocalPlayer(private val context: Context) {
    private var player: MediaPlayer? = null

    private val _playing = MutableStateFlow<Uri?>(null)
    val playing: StateFlow<Uri?> = _playing

    fun toggle(uri: Uri, onError: (String) -> Unit) {
        if (_playing.value == uri) { stop(); return }
        stop()
        try {
            val p = MediaPlayer()
            p.setDataSource(context, uri)
            p.setOnCompletionListener { stop() }
            p.setOnErrorListener { _, what, _ -> onError("Playback failed ($what)"); stop(); true }
            p.prepare()
            p.start()
            player = p
            _playing.value = uri
        } catch (e: Exception) {
            onError("Playback: ${e.message}")
            stop()
        }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _playing.value = null
    }
}
