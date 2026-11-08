package de.koii.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Plays a [PcmAudio] (or a part of it) on the phone's own speaker, for auditioning edits. */
class PreviewPlayer(private val scope: CoroutineScope) {
    private var job: Job? = null

    private val _playing = MutableStateFlow(false)
    val playing: StateFlow<Boolean> = _playing

    private val _position = MutableStateFlow(0)
    /** Frame currently playing, relative to the start of the sample. */
    val position: StateFlow<Int> = _position

    fun play(audio: PcmAudio, range: FrameRange? = null, onError: (String) -> Unit = {}) {
        stop()
        val r = FrameRange((range?.start ?: 0).coerceIn(0, audio.frames), (range?.end ?: audio.frames).coerceIn(0, audio.frames))
        if (r.isEmpty()) return
        val mask = if (audio.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val rate = audio.sampleRate.coerceIn(4000, 192000)
        val min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_FLOAT)
        if (min <= 0) { onError("Audio format not supported"); return }
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(mask).build())
            .setBufferSizeInBytes(min * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        _playing.value = true
        job = scope.launch(Dispatchers.IO) {
            try {
                track.play()
                val step = maxOf(256, rate / 20)
                var frame = r.start
                while (isActive && frame < r.end) {
                    val n = minOf(step, r.end - frame)
                    val w = track.write(audio.samples, frame * audio.channels, n * audio.channels, AudioTrack.WRITE_BLOCKING)
                    if (w < 0) break
                    frame += w / audio.channels
                    _position.value = frame
                }
                if (isActive) track.stop()
            } catch (e: Exception) {
                onError(e.message ?: "Playback failed")
            } finally {
                track.release()
                _playing.value = false
                _position.value = 0
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _playing.value = false
    }
}
