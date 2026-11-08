package de.koii.companion.ui

import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(vm: KoiiViewModel) {
    val prefs = vm.g.prefs
    val channel by prefs.midiChannel.collectAsState()
    val velocity by prefs.velocity.collectAsState()
    val bits by prefs.recordBits.collectAsState()
    val theme by prefs.theme.collectAsState()
    val loopDir by prefs.loopDir.collectAsState()
    val loops by vm.g.loops.loops.collectAsState()
    val scanning by vm.g.loops.scanning.collectAsState()
    val loopError by vm.g.loops.error.collectAsState()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.setLoopFolder(uri)
    }

    Screen("Settings", actions = { TeButton("Back", { vm.back() }) }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Label("Theme")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Palettes.all.forEach { p ->
                    TeButton(p.label, { prefs.setTheme(p.id) }, Modifier.weight(1f), selected = theme == p.id)
                }
            }

            Label("Loop folder (optional)")
            Text(
                loopDir?.let { folderLabel(it) } ?: "Not set. Pick a folder on your phone with loops, then put them on pads from Pads → Assign → Loops.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (loopDir != null) {
                Text(
                    if (scanning) "Scanning…" else "${loops.size} audio files found",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            loopError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Gap(6)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton(if (loopDir == null) "Choose folder" else "Change", { picker.launch(null) }, Modifier.weight(1f))
                if (loopDir != null) {
                    TeButton("Rescan", { vm.g.loops.refresh() }, Modifier.weight(1f))
                    TeButton("Clear", { vm.g.loops.clearFolder() }, Modifier.weight(1f))
                }
            }

            Label("MIDI channel")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeButton("−", { prefs.setMidiChannel(channel - 1) })
                Text("CH $channel", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TeButton("+", { prefs.setMidiChannel(channel + 1) })
            }
            Label("Pad velocity $velocity")
            TeSlider(velocity.toFloat(), { prefs.setVelocity(it.toInt()) }, valueRange = 1f..127f)

            Label("Recording bit depth")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(16 to "16", 24 to "24", 32 to "32 float").forEach { (b, l) ->
                    TeButton(l, { prefs.setRecordBits(b) }, Modifier.weight(1f), selected = bits == b)
                }
            }

            Label("About")
            KeyValue("Version", "0.2.0")
            KeyValue("Protocol", "TE SysEx file protocol, USB-MIDI bulk")
            Gap()
            Text(
                "Unverified on hardware: slot moves, previews, metadata writes, project download/upload, live events. " +
                    "Chords and the arpeggiator send plain notes on the MIDI channel above.",
                style = MaterialTheme.typography.bodySmall,
            )
            Gap(12)
        }
    }
}

/** Readable name of a tree URI, e.g. "primary:Music/Loops". */
private fun folderLabel(uri: String): String =
    runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(uri)) }.getOrDefault(uri).replace(":", " / ")
