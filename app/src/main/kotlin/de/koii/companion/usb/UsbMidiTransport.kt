package de.koii.companion.usb

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import de.koii.protocol.MidiTransport
import de.koii.protocol.UsbMidiCodec
import de.koii.protocol.UsbMidiDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Talks to the MIDI streaming interface through the raw bulk endpoints. android.media.midi is avoided on
 * purpose: it drops bytes on large SysEx blocks.
 */
class UsbMidiTransport(
    private val connection: UsbDeviceConnection,
    private val midiInterface: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint,
    scope: CoroutineScope,
) : MidiTransport {

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4096)
    override val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    private val writeLock = Mutex()
    @Volatile private var closed = false

    private val reader: Job = scope.launch(Dispatchers.IO) {
        val decoder = UsbMidiDecoder { _incoming.tryEmit(it) }
        val buf = ByteArray(maxOf(inEndpoint.maxPacketSize, 64))
        while (isActive && !closed) {
            val n = connection.bulkTransfer(inEndpoint, buf, buf.size, READ_TIMEOUT_MS)
            if (n > 0) decoder.feed(buf, n)
            // n < 0 is a timeout while idle (or a vanished device, which the manager reports separately)
        }
    }

    override suspend fun send(message: ByteArray) {
        val packets = UsbMidiCodec.encode(message)
        writeLock.withLock {
            withContext(Dispatchers.IO) {
                var off = 0
                while (off < packets.size) {
                    val len = minOf(WRITE_BLOCK, packets.size - off)
                    val n = connection.bulkTransfer(outEndpoint, packets, off, len, WRITE_TIMEOUT_MS)
                    if (n < 0) throw IOException("USB bulk write failed at offset $off")
                    off += n
                }
            }
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        reader.cancel()
        runCatching { connection.releaseInterface(midiInterface) }
        runCatching { connection.close() }
    }

    private companion object {
        const val READ_TIMEOUT_MS = 100
        const val WRITE_TIMEOUT_MS = 2000
        const val WRITE_BLOCK = 1024 // multiple of 4 (USB-MIDI packet size)
    }
}
