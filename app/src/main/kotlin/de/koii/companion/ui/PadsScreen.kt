package de.koii.companion.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.koii.companion.data.Link
import de.koii.companion.data.PadRepository
import de.koii.companion.midi.MidiController

/** Remote pad grid: trigger pads from the phone, set transport/clock, or open the pad assignment. */
@Composable
fun PadsScreen(vm: KoiiViewModel) {
    val g = vm.g
    val link by g.session.link.collectAsState()
    val held by g.midi.held.collectAsState()
    val clock by g.midi.clockRunning.collectAsState()
    val velocity by g.prefs.velocity.collectAsState()
    val bpm by g.prefs.bpm.collectAsState()

    var group by rememberSaveable { mutableStateOf(0) }
    var assignMode by rememberSaveable { mutableStateOf(false) }
    var project by rememberSaveable { mutableStateOf(1) }
    val online = link is Link.Online

    Screen("Pads") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PadRepository.GROUPS.forEachIndexed { i, c ->
                TeButton(c.toString(), { group = i }, Modifier.weight(1f), selected = group == i)
            }
        }
        Gap(8)

        // Rows top → bottom: 10 11 12 / 7 8 9 / 4 5 6 / 1 2 3
        for (row in 3 downTo 0) {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (col in 0..2) {
                    val pad = row * 3 + col // 0-based
                    val note = MidiController.padNote(group, pad)
                    var pressed by remember { mutableStateOf(false) }
                    TeCell(
                        modifier = Modifier.weight(1f).aspectRatio(1.6f).pointerInput(note, assignMode, online) {
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
                        filled = true,
                    ) { fg ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${pad + 1}", style = MaterialTheme.typography.titleMedium, color = fg)
                                Text("N$note", style = MaterialTheme.typography.labelSmall, color = fg)
                            }
                        }
                    }
                }
            }
        }

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
        Slider(velocity.toFloat(), { g.prefs.setVelocity(it.toInt()) }, valueRange = 1f..127f)
        Gap(4)
        TeButton(if (assignMode) "Assign mode: tap a pad" else "Assign sounds to pads", { assignMode = !assignMode }, Modifier.fillMaxWidth(), selected = assignMode, enabled = online)
        if (assignMode) {
            Label("Project")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (p in 1..PadRepository.PROJECTS) TeButton("$p", { project = p }, Modifier.weight(1f), selected = project == p)
            }
        }
    }
}

@Composable
fun PadAssignScreen(vm: KoiiViewModel, target: Sub.PadAssign) {
    val g = vm.g
    val slots by g.sounds.slots.collectAsState()
    val names by g.sounds.names.collectAsState()
    var current by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(target) {
        loaded = false
        current = runCatching { g.pads.assignment(target.project, target.group, target.pad) }.getOrNull()
        loaded = true
    }

    Screen("Assign", actions = { TeButton("Back", { vm.back() }) }) {
        Text(
            "P%02d · GROUP %c · PAD %02d".format(target.project, target.group, target.pad),
            style = MaterialTheme.typography.titleMedium,
        )
        val cur = current
        Text(
            if (!loaded) "Reading assignment…" else if (cur == null) "No sound assigned." else "Now: ${"%03d".format(cur)} ${names[cur].orEmpty()}",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp),
        )
        Rule()
        LazyColumn(Modifier.weight(1f)) {
            items(slots.keys.sorted(), key = { it }) { id ->
                LaunchedEffect(id) { vm.loadNames(listOf(id)) }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("%03d  %s".format(id, names[id] ?: slots[id]?.name.orEmpty()), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TeButton("Play", { vm.preview(id) })
                    Box(Modifier.padding(start = 6.dp)) {
                        TeButton("Use", {
                            vm.op("Assign") {
                                g.pads.assign(target.project, target.group, target.pad, id)
                                current = id
                            }
                        }, selected = current == id)
                    }
                }
                Rule()
            }
        }
    }
}
