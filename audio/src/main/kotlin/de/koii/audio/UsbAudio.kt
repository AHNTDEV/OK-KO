package de.koii.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs

/**
 * The K.O. II (FW 2.5+) shows up as a class-compliant USB audio device. Android's own USB audio driver owns
 * that interface, so apps do not claim it through UsbManager: they pick it via AudioManager and route to it.
 */
object UsbAudioDevices {
    private fun usb(d: AudioDeviceInfo) = d.type == AudioDeviceInfo.TYPE_USB_DEVICE || d.type == AudioDeviceInfo.TYPE_USB_HEADSET

    fun input(context: Context): AudioDeviceInfo? =
        context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull(::usb)

    fun output(context: Context): AudioDeviceInfo? =
        context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull(::usb)

    /** Prefers the device-native 46875 Hz, then 48000; an empty list from the driver means "anything". */
    fun pickSampleRate(d: AudioDeviceInfo): Int {
        val rates = d.sampleRates
        return when {
            rates.isEmpty() -> 48000
            46875 in rates -> 46875
            48000 in rates -> 48000
            else -> rates.max()
        }
    }

    fun pickChannels(d: AudioDeviceInfo): Int {
        val ch = d.channelCounts
        return if (ch.isEmpty()) 2 else minOf(2, ch.max())
    }
}

/** Streams float samples as WAV to a seekable channel (e.g. a SAF ParcelFileDescriptor) and patches the header on finish. */
class WavStreamWriter(
    private val channel: FileChannel,
    private val channels: Int,
    private val sampleRate: Int,
    /** 16, 24 (PCM) or 32 (IEEE float). */
    private val bitDepth: Int = 24,
) {
    private var dataBytes = 0L
    private val bytesPer = bitDepth / 8

    init {
        require(bitDepth == 16 || bitDepth == 24 || bitDepth == 32) { "bitDepth must be 16, 24 or 32" }
        channel.write(header(0))
    }

    private fun header(dataLen: Long): ByteBuffer {
        val b = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt((36 + dataLen).coerceAtMost(0xFFFFFFFFL).toInt()).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(if (bitDepth == 32) 3 else 1).putShort(channels.toShort())
        b.putInt(sampleRate).putInt(sampleRate * channels * bytesPer).putShort((channels * bytesPer).toShort()).putShort(bitDepth.toShort())
        b.put("data".toByteArray()).putInt(dataLen.coerceAtMost(0xFFFFFFFFL).toInt())
        b.flip()
        return b
    }

    fun write(samples: FloatArray, count: Int) {
        val b = ByteBuffer.allocate(count * bytesPer).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) {
            val s = samples[i]
            when (bitDepth) {
                16 -> b.putShort(Math.round(s.coerceIn(-1f, 1f) * 32767f).toShort())
                24 -> {
                    val v = Math.round(s.coerceIn(-1f, 1f) * 8388607f)
                    b.put(v.toByte()).put((v shr 8).toByte()).put((v shr 16).toByte())
                }
                else -> b.putFloat(s)
            }
        }
        b.flip()
        while (b.hasRemaining()) channel.write(b)
        dataBytes += count * bytesPer
    }

    fun finish() {
        channel.position(0)
        channel.write(header(dataBytes))
        channel.force(false)
    }
}

data class RecordingResult(val frames: Long, val sampleRate: Int, val channels: Int) {
    val seconds: Double get() = frames.toDouble() / sampleRate
}

/** Records the K.O. II's stereo output (performance capture) from the USB audio interface. Needs RECORD_AUDIO. */
class UsbAudioRecorder(private val context: Context, private val scope: CoroutineScope) {
    private var job: Job? = null

    private val _level = MutableStateFlow(0f)
    /** Peak of the last buffer, 0..1. */
    val level: StateFlow<Float> = _level

    private val _frames = MutableStateFlow(0L)
    val frames: StateFlow<Long> = _frames

    val isRecording: Boolean get() = job?.isActive == true

    /**
     * Starts recording into [target]. [onFinished] runs on the recorder thread after the WAV header is patched.
     * Throws [IllegalStateException] if no USB audio input is present (device not on FW 2.5+, or not connected).
     */
    @SuppressLint("MissingPermission")
    fun start(target: FileChannel, bitDepth: Int = 24, onFinished: (Result<RecordingResult>) -> Unit) {
        check(!isRecording) { "Already recording" }
        val device = UsbAudioDevices.input(context) ?: error("No USB audio input found")
        val rate = UsbAudioDevices.pickSampleRate(device)
        val channels = UsbAudioDevices.pickChannels(device)
        val mask = if (channels == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val minBuf = AudioRecord.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_FLOAT)
        val record = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(rate).setChannelMask(mask).build(),
            )
            .setBufferSizeInBytes(maxOf(minBuf, rate * channels * 4 / 2))
            .build()
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord could not be initialised" }
        record.setPreferredDevice(device)

        _frames.value = 0
        job = scope.launch(Dispatchers.IO) {
            val writer = WavStreamWriter(target, channels, rate, bitDepth)
            val buf = FloatArray(rate / 10 * channels)
            var total = 0L
            val result = runCatching {
                record.startRecording()
                while (isActive) {
                    val n = record.read(buf, 0, buf.size, AudioRecord.READ_BLOCKING)
                    if (n < 0) error("AudioRecord.read returned $n")
                    if (n == 0) continue
                    writer.write(buf, n)
                    total += n / channels
                    _frames.value = total
                    var peak = 0f
                    for (i in 0 until n) peak = maxOf(peak, abs(buf[i]))
                    _level.value = peak
                }
            }
            runCatching { record.stop() }
            record.release()
            _level.value = 0f
            runCatching { writer.finish() }
            onFinished(result.map { RecordingResult(total, rate, channels) })
        }
    }

    fun stop() {
        job?.cancel()
    }
}

/** Plays decoded audio into the K.O. II's USB audio input (phone → device sampling). */
class UsbAudioPlayer(private val context: Context, private val scope: CoroutineScope) {
    private var job: Job? = null
    val isPlaying: Boolean get() = job?.isActive == true

    fun play(audio: PcmAudio, onDone: (Throwable?) -> Unit = {}) {
        stop()
        val device = UsbAudioDevices.output(context) ?: error("No USB audio output found")
        val mask = if (audio.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBuf = AudioTrack.getMinBufferSize(audio.sampleRate, mask, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(audio.sampleRate).setChannelMask(mask).build())
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.setPreferredDevice(device)
        job = scope.launch(Dispatchers.IO) {
            var failure: Throwable? = null
            try {
                track.play()
                var pos = 0
                val step = audio.sampleRate / 20 * audio.channels
                while (isActive && pos < audio.samples.size) {
                    val n = minOf(step, audio.samples.size - pos)
                    val w = track.write(audio.samples, pos, n, AudioTrack.WRITE_BLOCKING)
                    if (w < 0) error("AudioTrack.write returned $w")
                    pos += w
                }
                if (isActive) track.stop()
            } catch (t: Throwable) {
                failure = t
            } finally {
                track.release()
                onDone(failure)
            }
        }
    }

    fun stop() {
        job?.cancel()
    }
}
