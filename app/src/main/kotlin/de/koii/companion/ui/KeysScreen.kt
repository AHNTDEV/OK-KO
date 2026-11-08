package de.koii.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.koii.companion.data.Link
import de.koii.companion.midi.Arp
import de.koii.companion.midi.ArpMode
import de.koii.companion.midi.ArpRate
import de.koii.companion.midi.Chords
import de.koii.companion.midi.Extension
import de.koii.companion.midi.KeysConfig
import de.koii.companion.midi.Quality

private val WHITE = listOf(0, 2, 4, 5, 7, 9, 11)
/** Semitone and the white key it sits after (0-based index within the octave), for black keys. */
private val BLACK = listOf(1 to 0, 3 to 1, 6 to 3, 8 to 4, 10 to 5)
private const val WHITE_KEYS = 14

/** Play the device from a keyboard: single notes, chords with 6/7/9 and low octave, optional arpeggiator. */
@Composable
fun KeysScreen(vm: KoiiViewModel) {
    val g = vm.g
    val link by g.session.link.collectAsState()
    val cfg by g.keys.config.collectAsState()
    val down by g.keys.down.collectAsState()
    val bpm by g.prefs.bpm.collectAsState()
    val online = link is Link.Online

    DisposableEffect(Unit) { onDispose { g.keys.releaseAll() } }

    Screen("Keys") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (!online) {
                Text(
                    "Connect the K.O. II with a USB-C data cable. The keys play it as soon as it is online.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Gap(10)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TeButton("Note", { g.keys.update { it.copy(quality = null, extension = null) } }, Modifier.weight(1f), selected = cfg.quality == null)
                Quality.entries.forEach { q ->
                    TeButton(q.label, { g.keys.update { it.copy(quality = q) } }, Modifier.weight(1f), selected = cfg.quality == q)
                }
            }
            Gap(4)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Extension.entries.forEach { e ->
                    TeButton(
                        e.label, { g.keys.update { it.copy(extension = if (it.extension == e) null else e) } },
                        Modifier.weight(1f), selected = cfg.extension == e, enabled = cfg.quality != null,
                    )
                }
                TeButton("Low", { g.keys.update { it.copy(lowerOctave = !it.lowerOctave) } }, Modifier.weight(1f), selected = cfg.lowerOctave)
                TeButton("Arp", { g.keys.update { it.copy(arp = !it.arp) } }, Modifier.weight(1f), selected = cfg.arp)
            }

            if (cfg.arp) {
                Label("Arpeggiator, $bpm bpm")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ArpMode.entries.forEach { m ->
                        TeButton(m.label, { g.keys.update { it.copy(arpMode = m) } }, Modifier.weight(1f), selected = cfg.arpMode == m)
                    }
                }
                Gap(4)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ArpRate.entries.forEach { r ->
                        TeButton(r.label, { g.keys.update { it.copy(arpRate = r) } }, Modifier.weight(1f), selected = cfg.arpRate == r)
                    }
                }
                Gap(4)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    (1..3).forEach { o ->
                        TeButton("$o oct", { g.keys.update { it.copy(arpOctaves = o) } }, Modifier.weight(1f), selected = cfg.arpOctaves == o)
                    }
                    TeButton("−", { g.prefs.setBpm(bpm - 1) })
                    TeButton("+", { g.prefs.setBpm(bpm + 1) })
                }
            }

            Gap(10)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeButton("−", { g.keys.update { it.copy(octave = (it.octave - 1).coerceAtLeast(0)) } })
                Text(
                    "${Chords.noteName((cfg.octave + 1) * 12)} – ${Chords.noteName((cfg.octave + 1) * 12 + 23)}",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                )
                TeButton("+", { g.keys.update { it.copy(octave = (it.octave + 1).coerceAtMost(7)) } })
            }
            Gap(8)
            Keyboard(cfg, down, enabled = online, onPress = { g.keys.press(it) }, onRelease = { g.keys.release(it) })
            Gap(8)
            val preview = cfg.quality?.let { q ->
                Chords.notes((cfg.octave + 1) * 12, q, cfg.extension, cfg.lowerOctave).joinToString(" ") { Chords.noteName(it) }
            }
            if (preview != null) Text("A C chord plays $preview.", style = MaterialTheme.typography.bodySmall)
            Text(
                "Notes go out on MIDI channel ${g.prefs.midiChannel.collectAsState().value}. Set the K.O. II to listen on it.",
                style = MaterialTheme.typography.bodySmall,
            )
            Gap(12)
        }
    }
}

@Composable
private fun Keyboard(
    cfg: KeysConfig,
    down: Set<Int>,
    enabled: Boolean,
    onPress: (Int) -> Unit,
    onRelease: (Int) -> Unit,
) {
    val baseNote = (cfg.octave + 1) * 12
    BoxWithConstraints(Modifier.fillMaxWidth().height(220.dp)) {
        val keyW = maxWidth / WHITE_KEYS
        val blackW = keyW * 0.62f
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until WHITE_KEYS) {
                val note = baseNote + (i / 7) * 12 + WHITE[i % 7]
                val on = note in down
                Box(
                    Modifier.width(keyW).fillMaxHeight().padding(horizontal = 1.dp)
                        .background(if (on) Ink.Accent else Ink.Fg, RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .keyTouch(note, enabled, onPress, onRelease),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (i % 7 == 0) {
                        Text(Chords.noteName(note), style = MaterialTheme.typography.labelSmall, color = Ink.Bg, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
        }
        for (octave in 0..1) {
            for ((semi, after) in BLACK) {
                val note = baseNote + octave * 12 + semi
                val on = note in down
                Box(
                    Modifier.offset(x = keyW * (octave * 7 + after + 1) - blackW / 2).width(blackW).height(130.dp)
                        .background(if (on) Ink.Accent else Ink.Bg, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .border(1.dp, Ink.Edge, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .keyTouch(note, enabled, onPress, onRelease),
                )
            }
        }
    }
}

private fun Modifier.keyTouch(note: Int, enabled: Boolean, onPress: (Int) -> Unit, onRelease: (Int) -> Unit): Modifier =
    pointerInput(note, enabled) {
        detectTapGestures(onPress = {
            if (enabled) {
                onPress(note)
                tryAwaitRelease()
                onRelease(note)
            }
        })
    }
