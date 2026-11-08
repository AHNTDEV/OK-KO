package de.koii.companion.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.koii.companion.midi.Chords
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private class Param(val key: String, val label: String, val min: Int, val max: Int, val default: Int)

/** Ranges from the protocol validators (docs/protocol.md). */
private val PARAMS = listOf(
    Param("sound.rootnote", "Root note", 1, 127, 60),
    Param("sound.pitch", "Pitch", -12, 12, 0),
    Param("sound.pan", "Pan", -16, 16, 0),
    Param("sound.amplitude", "Volume", 0, 200, 100),
    Param("envelope.attack", "Attack", 0, 255, 0),
    Param("envelope.release", "Release", 0, 255, 255),
    Param("sound.bpm", "BPM", 60, 180, 120),
)

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt()

@Composable
fun SoundDetailScreen(vm: KoiiViewModel, id: Int) {
    val detail by vm.detail.collectAsState()
    val names by vm.g.sounds.names.collectAsState()
    var renaming by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
        if (uri != null) vm.downloadSound(id, uri)
    }

    val d = detail?.takeIf { it.id == id }
    val meta = d?.meta
    // Edited values by key; untouched keys keep the device value.
    var edits by remember(id) { mutableStateOf(mapOf<String, Int>()) }

    Screen(
        "%03d %s".format(id, names[id].orEmpty()),
        actions = { TeButton("Back", { vm.back() }) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when {
                d?.error != null -> Text("Error: ${d.error}", style = MaterialTheme.typography.bodySmall)
                d?.peaks != null -> Waveform(d.peaks)
                else -> Text("Loading the waveform…", style = MaterialTheme.typography.bodySmall)
            }
            Gap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Play", { vm.preview(id) }, Modifier.weight(1f))
                TeButton("Rename", { renaming = true }, Modifier.weight(1f))
                TeButton("Save WAV", { save.launch((names[id] ?: "sound") + ".wav") }, Modifier.weight(1f))
            }

            if (meta == null) {
                if (d?.error == null) Text("Reading metadata…", style = MaterialTheme.typography.bodySmall)
            } else {
                for (p in PARAMS) {
                    val value = edits[p.key] ?: meta.int(p.key) ?: p.default
                    val unset = meta.int(p.key) == null && p.key !in edits
                    val shown = if (p.key == "sound.rootnote") "$value (${Chords.noteName(value)})" else "$value"
                    Label(p.label + "  " + shown + if (unset) " (not set)" else "")
                    TeSlider(
                        value.toFloat(), { edits = edits + (p.key to it.toInt()) },
                        valueRange = p.min.toFloat()..p.max.toFloat(),
                    )
                }
                Gap(6)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeButton("Reset", { edits = emptyMap() }, Modifier.weight(1f), enabled = edits.isNotEmpty())
                    TeButton("Save changes", { vm.saveSoundMeta(id, edits); edits = emptyMap() }, Modifier.weight(2f), selected = edits.isNotEmpty(), enabled = edits.isNotEmpty())
                }
                Text(
                    "Only the values you changed are sent. Writing metadata is not yet checked on a real K.O. II.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
                )
            }
            Gap(16)
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(names[id].orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            confirmButton = { TextButton({ vm.rename(id, text); renaming = false }) { Text("OK") } },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancel") } },
            title = { Text("Rename ${"%03d".format(id)}") },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, singleLine = true)
                    Text("Shown as: " + de.koii.protocol.Names.normalizeSlotName(text), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}
