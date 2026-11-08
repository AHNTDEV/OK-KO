package de.koii.companion.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.koii.companion.data.Link
import de.koii.companion.data.PadRepository
import de.koii.companion.seq.Pattern
import de.koii.companion.seq.PatternBank

private val CELL = 44.dp
private val GAP = 3.dp

/** Step sequencer: plays pad notes on the K.O. II from the phone, tempo and swing included. */
@Composable
fun BeatsScreen(vm: KoiiViewModel) {
    val g = vm.g
    val bank by g.patterns.bank.collectAsState()
    val seq by g.sequencer.state.collectAsState()
    val link by g.session.link.collectAsState()
    val group by vm.seqGroup.collectAsState()
    val sendClock by vm.seqSendClock.collectAsState()
    val pattern = bank.current
    val online = link is Link.Online
    val groupColor = Ink.group(group)

    val exportMidi = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/midi")) { uri: Uri? -> if (uri != null) vm.seqExportMidi(uri) }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? -> if (uri != null) vm.seqExportJson(uri) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.seqImport(uri) }

    DisposableEffect(Unit) { onDispose { } }

    Screen("Beats") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!online) {
                Text(
                    "Connect the K.O. II with a USB-C data cable. The sequencer plays its pads as soon as it is online.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Gap(10)
            }

            // pattern slots
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until PatternBank.SLOTS) {
                    TeButton("${i + 1}", { g.patterns.select(i) }, Modifier.weight(1f), selected = bank.selected == i)
                }
            }
            Gap(8)

            // transport
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                TeButton(
                    if (seq.running) "Stop" else "Play", { vm.seqToggle() }, Modifier.weight(1f),
                    selected = seq.running, enabled = online,
                )
                TeButton("-", { g.patterns.edit { it.withBpm(it.bpm - 1) } })
                Text("${pattern.bpm} bpm", style = MaterialTheme.typography.titleMedium)
                TeButton("+", { g.patterns.edit { it.withBpm(it.bpm + 1) } })
            }
            Gap(4)
            Text("Swing ${pattern.swing}%", style = MaterialTheme.typography.titleSmall)
            TeSlider(pattern.swing.toFloat(), { v -> g.patterns.edit { it.withSwing(v.toInt()) } }, valueRange = 0f..60f)

            // length and group
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pattern.LENGTHS.forEach { n ->
                    TeButton("$n steps", { g.patterns.edit { it.withLength(n) } }, Modifier.weight(1f), selected = pattern.length == n)
                }
            }
            Gap(6)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PadRepository.GROUPS.forEachIndexed { i, c ->
                    TeButton("Group $c", { vm.seqGroup.value = i }, Modifier.weight(1f), selected = group == i, tint = Ink.group(i))
                }
            }
            Gap(10)

            // step grid: pad labels stay put, the steps scroll sideways
            Row(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
                    Box(Modifier.height(24.dp))
                    for (pad in 11 downTo 0) {
                        val track = Pattern.track(group, pad)
                        Box(
                            Modifier.size(width = 40.dp, height = CELL).clip(RoundedCornerShape(8.dp))
                                .border(1.dp, groupColor, RoundedCornerShape(8.dp))
                                .combinedClickable(onLongClick = { g.patterns.edit { it.withoutTrack(track) } }, onClick = { vm.seqAudition(group, pad) }),
                            contentAlignment = Alignment.Center,
                        ) { Text("${pad + 1}", style = MaterialTheme.typography.titleSmall) }
                    }
                }
                Column(Modifier.horizontalScroll(rememberScrollState()).padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(GAP)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                        for (s in 0 until pattern.length) {
                            Box(Modifier.width(CELL).height(24.dp), contentAlignment = Alignment.Center) {
                                if (s % 4 == 0) Text("${s / 4 + 1}", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    for (pad in 11 downTo 0) {
                        val track = Pattern.track(group, pad)
                        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                            for (s in 0 until pattern.length) {
                                val v = pattern.cell(track, s)
                                val now = seq.running && seq.step == s
                                val shape = RoundedCornerShape(8.dp)
                                Box(
                                    Modifier.size(CELL).clip(shape)
                                        .background(if (v > 0) groupColor else if (s / 4 % 2 == 0) Ink.Surface else Ink.Bg)
                                        .border(if (now) 2.dp else 1.dp, if (now) Ink.Fg else if (v > 0) groupColor else Ink.Faint, shape)
                                        .combinedClickable(onClick = { g.patterns.edit { it.cycle(track, s) } }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (v >= Pattern.VEL_ACCENT) Box(Modifier.size(12.dp).clip(CircleShape).background(Ink.Bg))
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Tap a step once for a hit, twice for an accent, three times to clear it. Tap a pad number to hear it, press and hold to clear its row.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp),
            )

            Check("Also send MIDI start and clock to the K.O. II", sendClock) { vm.seqSendClock.value = it }
            Text(
                "Off: the phone plays the pads by itself. On: the K.O. II also gets tempo and start, so its own sequencer follows.",
                style = MaterialTheme.typography.bodySmall,
            )

            Label("This pattern")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Clear", { g.patterns.edit { it.cleared() } }, Modifier.weight(1f))
                TeButton("Copy to next", { g.patterns.copyToNext() }, Modifier.weight(1f))
            }
            Gap(6)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Export MIDI", { exportMidi.launch(safeName(pattern.name) + ".mid") }, Modifier.weight(1f))
                TeButton("Export file", { exportJson.launch(safeName(pattern.name) + ".json") }, Modifier.weight(1f))
            }
            Gap(6)
            TeButton("Import MIDI or pattern file", { import.launch(arrayOf("audio/midi", "audio/mid", "application/json", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth())
            Text(
                "Import replaces the pattern in the selected slot. MIDI notes C2 to B5 become pads A to D.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
            )
            Gap(16)
        }
    }
}

private fun safeName(n: String): String = n.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifEmpty { "pattern" }
