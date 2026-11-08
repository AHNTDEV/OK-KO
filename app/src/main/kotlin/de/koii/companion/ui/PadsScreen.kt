package de.koii.companion.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.koii.companion.data.Link
import de.koii.companion.data.PadRepository
import de.koii.companion.midi.MidiController

/** Bank view: every pad of the chosen project/group with its sample name, playable from the phone. */
@Composable
fun PadsScreen(vm: KoiiViewModel) {
    val g = vm.g
    val link by g.session.link.collectAsState()
    val held by g.midi.held.collectAsState()
    val clock by g.midi.clockRunning.collectAsState()
    val velocity by g.prefs.velocity.collectAsState()
    val bpm by g.prefs.bpm.collectAsState()
    val project by vm.padProject.collectAsState()
    val group by vm.padGroup.collectAsState()
    val bank by vm.bank.collectAsState()
    val names by g.sounds.names.collectAsState()
    val slots by g.sounds.slots.collectAsState()

    var assignMode by rememberSaveable { mutableStateOf(false) }
    val online = link is Link.Online

    LaunchedEffect(online, project, group) { if (online) vm.loadBank() }

    Screen("Pads") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Label("Project")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (p in 1..PadRepository.PROJECTS) {
                    TeButton("$p", { vm.padProject.value = p }, Modifier.weight(1f), selected = project == p)
                }
            }
            Gap(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PadRepository.GROUPS.forEachIndexed { i, c ->
                    TeButton(c.toString(), { vm.padGroup.value = i }, Modifier.weight(1f), selected = group == i, tint = Ink.group(i))
                }
            }
            Gap(8)

            // Rows top → bottom: 10 11 12 / 7 8 9 / 4 5 6 / 1 2 3
            for (row in 3 downTo 0) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (col in 0..2) {
                        val pad = row * 3 + col // 0-based
                        val note = MidiController.padNote(group, pad)
                        val soundId = bank[pad + 1]
                        var pressed by remember { mutableStateOf(false) }
                        TeCell(
                            modifier = Modifier.weight(1f).aspectRatio(1.4f).pointerInput(note, assignMode, online, project, group) {
                                detectTapGestures(onPress = {
                                    if (assignMode) {
                                        tryAwaitRelease()
                                        vm.open(Sub.PadAssign(project, PadRepository.GROUPS[group], pad + 1))
                                    } else if (online) {
                                        pressed = true
                                        vm.op("Pad") { g.midi.noteOn(note) }
                                        tryAwaitRelease()
                                        pressed = false
                                        vm.op("Pad") { g.midi.noteOff(note) }
                                    }
                                })
                            },
                            selected = pressed || note in held,
                            filled = soundId != null || assignMode,
                            tint = Ink.group(group),
                        ) { fg ->
                            Column {
                                Text("${pad + 1}", style = MaterialTheme.typography.titleMedium, color = fg)
                                Text(
                                    soundId?.let { names[it] ?: slots[it]?.name.orEmpty() } ?: if (assignMode) "tap to assign" else "",
                                    style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Play pads", { assignMode = false }, Modifier.weight(1f), selected = !assignMode)
                TeButton("Assign", { assignMode = true }, Modifier.weight(1f), selected = assignMode, enabled = online)
            }
            Label("Transport")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Play", { vm.op("Transport") { g.midi.start() } }, Modifier.weight(1f), enabled = online)
                TeButton("Stop", { vm.op("Transport") { g.midi.stop() } }, Modifier.weight(1f), enabled = online)
                TeButton("Cont", { vm.op("Transport") { g.midi.cont() } }, Modifier.weight(1f), enabled = online)
            }
            Gap(6)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TeButton("−", { g.prefs.setBpm(bpm - 1) })
                Text("$bpm BPM", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TeButton("+", { g.prefs.setBpm(bpm + 1) })
                TeButton("Clock", { if (clock) g.midi.stopClock() else g.midi.startClock(bpm) }, selected = clock, enabled = online)
            }
            Label("Velocity $velocity")
            TeSlider(velocity.toFloat(), { g.prefs.setVelocity(it.toInt()) }, valueRange = 1f..127f)
            Gap(8)
        }
    }
}

@Composable
fun PadAssignScreen(vm: KoiiViewModel, target: Sub.PadAssign) {
    val g = vm.g
    val slots by g.sounds.slots.collectAsState()
    val names by g.sounds.names.collectAsState()
    val bank by vm.bank.collectAsState()
    val loops by g.loops.loops.collectAsState()
    val scanning by g.loops.scanning.collectAsState()
    val loopError by g.loops.error.collectAsState()
    val loopDir by g.prefs.loopDir.collectAsState()
    val playing by g.localPlayer.playing.collectAsState()

    var source by rememberSaveable { mutableStateOf(0) } // 0 = device sounds, 1 = loops
    var filter by rememberSaveable { mutableStateOf("") }
    val current = bank[target.pad]

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.setLoopFolder(uri)
    }

    Screen("Assign", actions = { TeButton("Back", { g.localPlayer.stop(); vm.back() }) }) {
        Text(
            "P%02d · GROUP %c · PAD %02d".format(target.project, target.group, target.pad),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (current == null) "No sound assigned." else "Now: ${"%03d".format(current)} ${names[current].orEmpty()}",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp),
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TeButton("Device sounds", { source = 0 }, Modifier.weight(1f), selected = source == 0)
            TeButton("Loops", { source = 1 }, Modifier.weight(1f), selected = source == 1)
        }

        if (source == 0) {
            OutlinedTextField(filter, { filter = it }, label = { Text("FILTER") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val ids = slots.keys.sorted().filter { id ->
                filter.isBlank() || "%03d".format(id).contains(filter) || (names[id] ?: slots[id]?.name.orEmpty()).contains(filter, ignoreCase = true)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(ids, key = { it }) { id ->
                    LaunchedEffect(id) { vm.loadNames(listOf(id)) }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("%03d  %s".format(id, names[id] ?: slots[id]?.name.orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        TeButton("Play", { vm.preview(id) })
                        Box(Modifier.padding(start = 6.dp)) {
                            TeButton("Use", { vm.assignSound(target.project, target.group, target.pad, id) }, selected = current == id)
                        }
                    }
                    Rule()
                }
            }
        } else if (loopDir == null) {
            Text("Pick a folder on your phone that holds your loops. They are converted and uploaded when you put one on a pad.", style = MaterialTheme.typography.bodyMedium)
            Gap()
            TeButton("Choose loop folder", { folderPicker.launch(null) }, Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (scanning) "Scanning…" else "${loops.size} loops", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TeButton("Rescan", { g.loops.refresh() })
            }
            loopError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(filter, { filter = it }, label = { Text("FILTER") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val shown = loops.filter { filter.isBlank() || it.name.contains(filter, ignoreCase = true) || it.folder.contains(filter, ignoreCase = true) }
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.uri.toString() }) { loop ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(loop.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (loop.folder.isNotEmpty()) Text(loop.folder, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        TeButton(if (playing == loop.uri) "Stop" else "Play", { vm.toggleLoopPreview(loop) }, selected = playing == loop.uri)
                        Box(Modifier.padding(start = 6.dp)) {
                            TeButton("Use", { vm.assignLoop(loop, target.project, target.group, target.pad); vm.back() })
                        }
                    }
                    Rule()
                }
            }
        }
    }
}
