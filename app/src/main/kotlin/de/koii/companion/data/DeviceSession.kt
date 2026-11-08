package de.koii.companion.data

import de.koii.companion.usb.UsbDeviceManager
import de.koii.companion.usb.UsbState
import de.koii.protocol.DeviceInfo
import de.koii.protocol.FileEvent
import de.koii.protocol.KoiiClient
import de.koii.protocol.MidiTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

sealed interface Link {
    data object Offline : Link
    data object NeedsPermission : Link
    data class Connecting(val name: String) : Link
    data class Online(val usbName: String, val info: DeviceInfo, val hasUsbAudio: Boolean) : Link {
        /** USB audio needs FW 2.5+ and an audio streaming interface on the USB device. */
        val usbAudioAvailable: Boolean get() = hasUsbAudio && info.meetsMinimumOs(DeviceInfo.USB_AUDIO_OS)
    }
    data class Failed(val message: String) : Link
}

data class Storage(val freeBytes: Long, val totalBytes: Long)

/** Owns the KoiiClient for the lifetime of a USB connection. */
class DeviceSession(private val usb: UsbDeviceManager, private val scope: CoroutineScope) {
    private val _link = MutableStateFlow<Link>(Link.Offline)
    val link: StateFlow<Link> = _link

    private val _events = MutableSharedFlow<FileEvent>(extraBufferCapacity = 256)
    /** File events from the device (live sync). */
    val events: SharedFlow<FileEvent> = _events.asSharedFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    /** Last device debug lines (frames with marker 0x33). */
    val log: StateFlow<List<String>> = _log

    private val _storage = MutableStateFlow<Storage?>(null)
    val storage: StateFlow<Storage?> = _storage

    @Volatile var client: KoiiClient? = null
        private set
    @Volatile var transport: MidiTransport? = null
        private set

    private var jobs = emptyList<Job>()
    private var soundsNode = 0
    private var projectsNode = 0

    init {
        scope.launch {
            usb.state.collect { onUsb(it) }
        }
    }

    private suspend fun onUsb(s: UsbState) {
        when (s) {
            UsbState.NoDevice -> teardown(Link.Offline)
            is UsbState.NeedsPermission -> teardown(Link.NeedsPermission)
            is UsbState.Error -> teardown(Link.Failed(s.message))
            is UsbState.Connecting -> _link.value = Link.Connecting(s.name)
            is UsbState.Connected -> bringUp(s)
        }
    }

    private suspend fun bringUp(s: UsbState.Connected) {
        teardown(Link.Connecting(s.name))
        transport = s.transport
        val c = KoiiClient(s.transport, scope)
        client = c
        try {
            val info = c.connect(subscribe = true)
            if (!info.isKoII) throw IllegalStateException("Not a K.O. II (sku ${info.greet.sku} / ${info.greet.baseSku})")
            if (!info.meetsMinimumOs()) throw IllegalStateException("Firmware ${info.greet.osVersion} is older than ${DeviceInfo.MIN_OS}")
            soundsNode = c.resolve("/sounds")
            projectsNode = c.resolve("/projects")
            jobs = listOf(
                scope.launch { c.events.collect { _events.tryEmit(it) } },
                scope.launch { c.logs.collect { line -> _log.update { (it + line).takeLast(200) } } },
            )
            _link.value = Link.Online(s.name, info, s.hasAudio)
            refreshStorage()
        } catch (e: Exception) {
            _link.value = Link.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun teardown(next: Link) {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        client?.close()
        client = null
        transport = null
        _storage.value = null
        _link.value = next
    }

    fun requireClient(): KoiiClient = client ?: error("Device not connected")
    fun soundsNodeId(): Int = soundsNode.takeIf { it != 0 } ?: error("Device not connected")
    fun projectsNodeId(): Int = projectsNode.takeIf { it != 0 } ?: error("Device not connected")

    suspend fun refreshStorage() {
        val c = client ?: return
        runCatching {
            val m = c.getMetadata(soundsNode)
            val total = m["max_capacity"]?.jsonPrimitive?.longOrNull
            val free = m["free_space_in_bytes"]?.jsonPrimitive?.longOrNull
            if (total != null && free != null) _storage.value = Storage(free, total)
        }
    }

    fun retry() = usb.scan()
}
