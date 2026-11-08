package de.koii.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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

    Screen("Settings") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Label("MIDI channel")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeButton("−", { prefs.setMidiChannel(channel - 1) })
                Text("CH $channel", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TeButton("+", { prefs.setMidiChannel(channel + 1) })
            }
            Label("Pad velocity $velocity")
            Slider(velocity.toFloat(), { prefs.setVelocity(it.toInt()) }, valueRange = 1f..127f)
            Label("Recording")
            KeyValue("Bit depth", if (bits == 32) "32 float" else "$bits")

            Label("About")
            KeyValue("Version", "0.1.0")
            KeyValue("Protocol", "TE SysEx file protocol, USB-MIDI bulk")
            Gap()
            Text(
                "Unverified on hardware: move, preview, metadata write, project download/upload, live events. " +
                    "Verified by the community on EP-133 OS 2.5.1: identity, greet, init, list, metadata read, put, get, delete.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
