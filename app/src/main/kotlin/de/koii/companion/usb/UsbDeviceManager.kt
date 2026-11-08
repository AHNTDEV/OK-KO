package de.koii.companion.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import de.koii.protocol.MidiTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface UsbState {
    data object NoDevice : UsbState
    data class NeedsPermission(val device: UsbDevice) : UsbState
    data class Connecting(val name: String) : UsbState
    data class Connected(val name: String, val transport: MidiTransport, val hasAudio: Boolean) : UsbState
    data class Error(val message: String) : UsbState
}

/** Discovers the K.O. II, asks for permission and claims its MIDI interface (the audio interface stays with Android). */
class UsbDeviceManager(private val context: Context, private val scope: CoroutineScope) {
    private val usb = context.getSystemService(UsbManager::class.java)
    private val _state = MutableStateFlow<UsbState>(UsbState.NoDevice)
    val state: StateFlow<UsbState> = _state

    private var current: UsbDevice? = null
    private var transport: UsbMidiTransport? = null
    private var registered = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            val device: UsbDevice? = if (android.os.Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            when (intent.action) {
                ACTION_PERMISSION -> if (device != null) {
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) connect(device)
                    else _state.value = UsbState.Error("USB permission denied")
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> if (device != null && device.deviceId == current?.deviceId) disconnect()
            }
        }
    }

    fun start() {
        if (!registered) {
            val filter = IntentFilter().apply {
                addAction(ACTION_PERMISSION)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        scan()
    }

    /** Looks for a device with a MIDI streaming interface, preferring Teenage Engineering's vendor id. */
    fun scan() {
        if (_state.value is UsbState.Connected || _state.value is UsbState.Connecting) return
        val candidates = usb.deviceList.values.filter { midiInterface(it) != null }
        val device = candidates.firstOrNull { it.vendorId == TE_VENDOR_ID } ?: candidates.firstOrNull()
        if (device == null) {
            _state.value = UsbState.NoDevice
            return
        }
        if (usb.hasPermission(device)) connect(device) else requestPermission(device)
    }

    fun requestPermission(device: UsbDevice) {
        _state.value = UsbState.NeedsPermission(device)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(context, 0, Intent(ACTION_PERMISSION).setPackage(context.packageName), flags)
        usb.requestPermission(device, pi)
    }

    private fun connect(device: UsbDevice) {
        val name = device.productName ?: "USB ${device.vendorId.toString(16)}:${device.productId.toString(16)}"
        _state.value = UsbState.Connecting(name)
        val iface = midiInterface(device)
        if (iface == null) {
            _state.value = UsbState.Error("No MIDI interface on $name")
            return
        }
        val endpoints = (0 until iface.endpointCount).map { iface.getEndpoint(it) }
            .filter { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        val epIn = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_IN }
        val epOut = endpoints.firstOrNull { it.direction == UsbConstants.USB_DIR_OUT }
        if (epIn == null || epOut == null) {
            _state.value = UsbState.Error("MIDI interface has no bulk IN/OUT endpoints")
            return
        }
        val connection = usb.openDevice(device)
        if (connection == null) {
            _state.value = UsbState.Error("Could not open $name")
            return
        }
        if (!connection.claimInterface(iface, true)) {
            connection.close()
            _state.value = UsbState.Error("Could not claim the MIDI interface")
            return
        }
        current = device
        val t = UsbMidiTransport(connection, iface, epIn, epOut, scope)
        transport = t
        _state.value = UsbState.Connected(name, t, hasAudioStreaming(device))
    }

    fun disconnect() {
        transport?.close()
        transport = null
        current = null
        _state.value = UsbState.NoDevice
    }

    fun stop() {
        if (registered) {
            runCatching { context.unregisterReceiver(receiver) }
            registered = false
        }
        disconnect()
    }

    private fun midiInterface(d: UsbDevice): UsbInterface? =
        (0 until d.interfaceCount).map { d.getInterface(it) }.firstOrNull {
            it.interfaceClass == UsbConstants.USB_CLASS_AUDIO && it.interfaceSubclass == SUBCLASS_MIDI_STREAMING &&
                (0 until it.endpointCount).any { e -> it.getEndpoint(e).type == UsbConstants.USB_ENDPOINT_XFER_BULK }
        }

    private fun hasAudioStreaming(d: UsbDevice): Boolean =
        (0 until d.interfaceCount).map { d.getInterface(it) }.any {
            it.interfaceClass == UsbConstants.USB_CLASS_AUDIO && it.interfaceSubclass == SUBCLASS_AUDIO_STREAMING
        }

    companion object {
        const val TE_VENDOR_ID = 0x2367
        private const val ACTION_PERMISSION = "de.koii.companion.USB_PERMISSION"
        private const val SUBCLASS_AUDIO_STREAMING = 2
        private const val SUBCLASS_MIDI_STREAMING = 3
    }
}
