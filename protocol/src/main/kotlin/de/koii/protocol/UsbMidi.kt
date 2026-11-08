package de.koii.protocol

import kotlinx.coroutines.flow.SharedFlow

/**
 * Complete MIDI messages in both directions: a full SysEx (F0 … F7) or a single channel/system message.
 * Implemented on top of raw USB bulk endpoints in the app module.
 */
interface MidiTransport : AutoCloseable {
    val incoming: SharedFlow<ByteArray>
    suspend fun send(message: ByteArray)
}

/** USB-MIDI 1.0 event packet framing (4 bytes: cable/CIN + up to 3 MIDI bytes). */
object UsbMidiCodec {
    fun encode(message: ByteArray, cable: Int = 0): ByteArray {
        require(message.isNotEmpty()) { "empty MIDI message" }
        val cn = (cable and 0xF) shl 4
        val out = java.io.ByteArrayOutputStream(message.size + 4)
        fun packet(cin: Int, a: Int, b: Int, c: Int) {
            out.write(cn or cin); out.write(a); out.write(b); out.write(c)
        }
        fun at(i: Int) = message[i].toInt() and 0xFF

        if (at(0) == 0xF0) {
            var i = 0
            while (i < message.size) {
                val left = message.size - i
                when {
                    left > 3 -> { packet(0x4, at(i), at(i + 1), at(i + 2)); i += 3 }
                    left == 3 -> { packet(0x7, at(i), at(i + 1), at(i + 2)); i += 3 }
                    left == 2 -> { packet(0x6, at(i), at(i + 1), 0); i += 2 }
                    else -> { packet(0x5, at(i), 0, 0); i += 1 }
                }
            }
            return out.toByteArray()
        }
        val status = at(0)
        val b1 = if (message.size > 1) at(1) else 0
        val b2 = if (message.size > 2) at(2) else 0
        when {
            status >= 0xF8 -> packet(0xF, status, 0, 0)
            status == 0xF1 || status == 0xF3 -> packet(0x2, status, b1, 0)
            status == 0xF2 -> packet(0x3, status, b1, b2)
            status == 0xF6 -> packet(0x5, status, 0, 0)
            status in 0x80..0xEF -> packet(status shr 4, status, b1, b2)
            else -> throw IllegalArgumentException("unsupported MIDI status 0x${status.toString(16)}")
        }
        return out.toByteArray()
    }
}

/** Reassembles SysEx messages from USB-MIDI packets; short messages are emitted immediately. */
class UsbMidiDecoder(private val onMessage: (ByteArray) -> Unit) {
    private var sysex = java.io.ByteArrayOutputStream()
    private var inSysex = false

    fun feed(buf: ByteArray, length: Int) {
        var i = 0
        while (i + 4 <= length) {
            val cin = buf[i].toInt() and 0x0F
            val n = when (cin) {
                0x0, 0x1 -> 0
                0x2, 0x6, 0xC, 0xD -> 2
                0x5, 0xF -> 1
                else -> 3
            }
            if (n > 0) handle(cin, buf, i + 1, n)
            i += 4
        }
    }

    private fun handle(cin: Int, buf: ByteArray, off: Int, n: Int) {
        val first = buf[off].toInt() and 0xFF
        if (cin == 0xF && first >= 0xF8) { onMessage(byteArrayOf(buf[off])); return }
        if (cin == 0x4 || cin == 0x5 || cin == 0x6 || cin == 0x7) {
            if (first == 0xF0) { sysex.reset(); inSysex = true }
            if (inSysex) {
                sysex.write(buf, off, n)
                if (cin != 0x4) {
                    inSysex = false
                    onMessage(sysex.toByteArray())
                    sysex.reset()
                }
                return
            }
        }
        onMessage(buf.copyOfRange(off, off + n))
    }
}
