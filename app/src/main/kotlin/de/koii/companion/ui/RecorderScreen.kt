package de.koii.companion.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.koii.audio.UsbAudioDevices
import de.koii.companion.data.Link
import kotlinx.coroutines.delay

@Composable
fun RecorderScreen(vm: KoiiViewModel) {
    val g = vm.g
    val ctx = LocalContext.current
    val link by g.session.link.collectAsState()
    val level by g.recorder.level.collectAsState()
    val bits by g.prefs.recordBits.collectAsState()
    val last by vm.lastRecording.collectAsState()
    val frames by g.recorder.frames.collectAsState()

    val audioOk = (link as? Link.Online)?.usbAudioAvailable == true
    val inDevice = remember(link, frames) { UsbAudioDevices.input(ctx) }

    var recording by remember { mutableStateOf(false) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(recording) {
        while (recording) { elapsed = (SystemClock.elapsedRealtime() - startedAt) / 1000; delay(250) }
        recording = g.recorder.isRecording
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
        if (uri != null) { vm.startRecording(uri); startedAt = SystemClock.elapsedRealtime(); recording = g.recorder.isRecording }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saver.launch("ko2-take.wav") else vm.say("Recording needs the microphone permission (Android applies it to USB audio inputs too).")
    }
    val opener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.playFileToDevice(uri)
    }

    Screen("Recorder") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!audioOk) {
                Text(
                    "USB audio needs firmware 2.5 or newer and a connected K.O. II.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                KeyValue("Input", inDevice?.productName?.toString() ?: "not visible to Android")
            }

            Label("Bit depth")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(16 to "16", 24 to "24", 32 to "32 float").forEach { (b, l) ->
                    TeButton(l, { g.prefs.setRecordBits(b) }, Modifier.weight(1f), selected = bits == b, enabled = !recording)
                }
            }

            Gap()
            Text("%02d:%02d".format(elapsed / 60, elapsed % 60), style = MaterialTheme.typography.displayMedium)
            Meter(if (recording) level else 0f)
            Gap()
            TeButton(
                if (recording) "Stop" else "Record K.O. II output",
                {
                    if (recording) {
                        g.recorder.stop(); recording = false
                    } else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        saver.launch("ko2-take.wav")
                    } else {
                        perm.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                Modifier.fillMaxWidth(), selected = recording, enabled = audioOk,
            )
            Gap(6)
            TeButton(
                "Save last take as sample", { last?.let(vm::recordingAsSample) }, Modifier.fillMaxWidth(),
                enabled = last != null && !recording && link is Link.Online,
            )
            Text("Converted to 46875 Hz, 16 bit and at most 40 seconds, then put in the next free slot.", style = MaterialTheme.typography.bodySmall)

            Label("Play from your phone into the K.O. II")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Play file", { opener.launch(arrayOf("audio/*")) }, Modifier.weight(1f), enabled = audioOk)
                TeButton("Stop", { g.player.stop() }, Modifier.weight(1f), enabled = audioOk)
            }
            Text("The file plays into the K.O. II's USB input. Sample it on the device.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
