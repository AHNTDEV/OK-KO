package de.koii.companion

import android.app.Application
import de.koii.audio.UsbAudioPlayer
import de.koii.audio.UsbAudioRecorder
import de.koii.companion.data.BackupRepository
import de.koii.companion.data.DeviceSession
import de.koii.companion.data.LocalPlayer
import de.koii.companion.data.LoopRepository
import de.koii.companion.data.PadRepository
import de.koii.companion.data.Prefs
import de.koii.companion.data.SoundRepository
import de.koii.companion.data.TransferManager
import de.koii.companion.data.TrashRepository
import de.koii.companion.midi.KeyboardPlayer
import de.koii.companion.midi.MidiController
import de.koii.companion.usb.UsbDeviceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired object graph; lives as long as the process. */
class AppGraph(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs = Prefs(app)
    val usb = UsbDeviceManager(app, scope)
    val session = DeviceSession(usb, scope)
    val trash = TrashRepository(app)
    val sounds = SoundRepository(session, trash, scope)
    val pads = PadRepository(session)
    val backup = BackupRepository(session, sounds)
    val midi = MidiController(session, prefs, scope)
    val keys = KeyboardPlayer(midi, prefs, scope)
    val loops = LoopRepository(app, prefs, scope)
    val localPlayer = LocalPlayer(app)
    val transfers = TransferManager(app, scope)
    val recorder = UsbAudioRecorder(app, scope)
    val player = UsbAudioPlayer(app, scope)

    init {
        usb.start()
    }
}

class KoiiApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
