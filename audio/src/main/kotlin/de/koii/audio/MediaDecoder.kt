package de.koii.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteOrder

/** Decodes anything the platform can (mp3, m4a/aac, flac, ogg, …) to float PCM. Use as AudioConverter fallback. */
object MediaDecoder {
    fun decode(bytes: ByteArray): PcmAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(object : MediaDataSource() {
                override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
                    if (position >= bytes.size) return -1
                    val n = minOf(size, bytes.size - position.toInt())
                    System.arraycopy(bytes, position.toInt(), buffer, offset, n)
                    return n
                }
                override fun getSize(): Long = bytes.size.toLong()
                override fun close() {}
            })
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw UnsupportedAudioException("No audio track found")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return run(extractor, codec, format)
            } finally {
                codec.release()
            }
        } catch (e: UnsupportedAudioException) {
            throw e
        } catch (e: Exception) {
            throw UnsupportedAudioException("Cannot decode audio: ${e.message}")
        } finally {
            extractor.release()
        }
    }

    private fun run(extractor: MediaExtractor, codec: MediaCodec, inFormat: MediaFormat): PcmAudio {
        var rate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var isFloat = false
        var acc = FloatArray(1 shl 20)
        var count = 0
        fun ensure(extra: Int) {
            if (count + extra > acc.size) acc = acc.copyOf(maxOf(acc.size * 2, count + extra))
        }

        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            if (!inputDone) {
                val i = codec.dequeueInputBuffer(10_000)
                if (i >= 0) {
                    val buf = codec.getInputBuffer(i)!!
                    val n = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val o = codec.dequeueOutputBuffer(info, 10_000)
            when {
                o >= 0 -> {
                    val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    if (isFloat) {
                        val fb = buf.asFloatBuffer()
                        ensure(fb.remaining())
                        while (fb.hasRemaining()) acc[count++] = fb.get()
                    } else {
                        val sb = buf.asShortBuffer()
                        ensure(sb.remaining())
                        while (sb.hasRemaining()) acc[count++] = sb.get() / 32768f
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
                o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    isFloat = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
            }
        }
        return PcmAudio(rate, channels, acc.copyOf(count - count % channels))
    }
}
