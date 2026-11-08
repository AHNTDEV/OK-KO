package de.koii.companion.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import de.koii.audio.AudioConverter
import de.koii.audio.SampleOps
import de.koii.companion.data.Link

private enum class Drag { NONE, START, END, NEW }

/** Waveform with a draggable selection. Drag on empty space to select, drag a handle to adjust, tap to clear. */
@Composable
private fun EditorWaveform(
    peaks: FloatArray,
    selStart: Float,
    selEnd: Float,
    playhead: Float,
    onSelect: (Float, Float) -> Unit,
    onClear: () -> Unit,
) {
    val accent = Ink.Accent
    val faint = Ink.Faint
    val fg = Ink.Fg
    var width by remember { mutableStateOf(1f) }
    // pointerInput keeps its lambda; read the live selection through state so handles stay current
    var live by remember { mutableStateOf(selStart to selEnd) }
    live = selStart to selEnd
    var mode by remember { mutableStateOf(Drag.NONE) }
    var anchor by remember { mutableStateOf(0f) }

    Canvas(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(10.dp)).background(Ink.Surface)
            .pointerInput(Unit) { detectTapGestures(onTap = { onClear() }) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        val x = (o.x / width).coerceIn(0f, 1f)
                        val (s, e) = live
                        val slop = 28.dp.toPx() / width
                        mode = when {
                            e > s && kotlin.math.abs(x - s) < slop -> Drag.START
                            e > s && kotlin.math.abs(x - e) < slop -> Drag.END
                            else -> { anchor = x; Drag.NEW }
                        }
                        if (mode == Drag.NEW) onSelect(x, x)
                    },
                    onDrag = { change, _ ->
                        val x = (change.position.x / width).coerceIn(0f, 1f)
                        val (s, e) = live
                        when (mode) {
                            Drag.START -> onSelect(x, e)
                            Drag.END -> onSelect(s, x)
                            Drag.NEW -> onSelect(anchor, x)
                            Drag.NONE -> Unit
                        }
                    },
                    onDragEnd = { mode = Drag.NONE },
                    onDragCancel = { mode = Drag.NONE },
                )
            },
    ) {
        width = size.width
        val mid = size.height / 2f
        drawLine(faint, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1f)
        if (selEnd > selStart) {
            drawRect(accent.copy(alpha = 0.22f), Offset(selStart * size.width, 0f), Size((selEnd - selStart) * size.width, size.height))
        }
        if (peaks.isNotEmpty()) {
            val step = size.width / peaks.size
            for (i in peaks.indices) {
                val x = i * step + step / 2f
                val inSel = selEnd > selStart && x / size.width in selStart..selEnd
                val h = (peaks[i].coerceIn(0f, 1f) * mid * 0.95f).coerceAtLeast(1f)
                drawLine(if (inSel) accent else fg.copy(alpha = 0.55f), Offset(x, mid - h), Offset(x, mid + h), strokeWidth = (step * 0.7f).coerceAtLeast(1f), cap = StrokeCap.Butt)
            }
        }
        if (selEnd > selStart) {
            for (f in listOf(selStart, selEnd)) {
                drawLine(accent, Offset(f * size.width, 0f), Offset(f * size.width, size.height), strokeWidth = 4f)
            }
        }
        if (playhead > 0f) drawLine(fg, Offset(playhead * size.width, 0f), Offset(playhead * size.width, size.height), strokeWidth = 2f)
    }
}

@Composable
fun EditorScreen(vm: KoiiViewModel) {
    val e by vm.editor.collectAsState()
    val playing by vm.g.preview.playing.collectAsState()
    val position by vm.g.preview.position.collectAsState()
    val link by vm.g.session.link.collectAsState()
    val online = link is Link.Online
    var renaming by remember { mutableStateOf(false) }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.openEditorFromFile(uri) }
    val saveWav = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? -> if (uri != null) vm.editorExportWav(uri) }

    val a = e.audio
    Screen("Sample editor", actions = { TeButton("Back", { vm.leaveEditor() }) }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when {
                e.loading -> Text("Loading the sample…", style = MaterialTheme.typography.bodyMedium)
                e.error != null -> Text("Could not open the sample: ${e.error}", style = MaterialTheme.typography.bodyMedium)
                a == null -> Text("Nothing open. Choose a file to edit.", style = MaterialTheme.typography.bodyMedium)
            }

            if (a != null) {
                val tooLong = a.seconds > AudioConverter.MAX_SECONDS
                Text(
                    "${e.name}: ${"%.2f".format(a.seconds)} s, ${a.sampleRate} Hz, ${if (a.channels == 1) "mono" else "stereo"}",
                    style = MaterialTheme.typography.titleSmall,
                )
                if (tooLong) {
                    Text("The K.O. II takes at most 40 seconds. Crop or cut before saving to the device.", style = MaterialTheme.typography.bodySmall)
                }
                Gap(8)
                EditorWaveform(
                    peaks = e.peaks, selStart = e.selStart, selEnd = e.selEnd,
                    playhead = if (playing && a.frames > 0) position.toFloat() / a.frames else 0f,
                    onSelect = vm::editorSelect, onClear = vm::editorClearSelection,
                )
                val sel = e.range()
                Text(
                    if (sel != null) "Selection %.2f s to %.2f s".format(sel.start.toDouble() / a.sampleRate, sel.end.toDouble() / a.sampleRate)
                    else "Drag on the waveform to select a part. Tools then work on the selection.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                )
                Gap(8)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeButton(if (playing) "Stop" else "Play", { vm.editorPlay() }, Modifier.weight(1f), selected = playing)
                    TeButton("Undo", { vm.editorUndo() }, Modifier.weight(1f), enabled = e.undo.isNotEmpty())
                    TeButton("Select all", { vm.editorSelect(0f, 1f) }, Modifier.weight(1f))
                }

                Label("Cut and shape")
                ToolRow(
                    "Crop" to { vm.editorApply { x, r -> if (r != null) SampleOps.crop(x, r) else x } },
                    "Cut out" to { vm.editorApply { x, r -> if (r != null) SampleOps.cut(x, r) else x } },
                    "Silence" to { vm.editorApply { x, r -> if (r != null) SampleOps.silence(x, r) else x } },
                    "Trim" to { vm.editorApply { x, _ -> SampleOps.trimSilence(x) } },
                )
                Gap(6)
                ToolRow(
                    "Reverse" to { vm.editorApply(keepSelection = true) { x, r -> SampleOps.reverse(x, r) } },
                    "Fade in" to { vm.editorApply(keepSelection = true) { x, r -> SampleOps.fadeIn(x, r) } },
                    "Fade out" to { vm.editorApply(keepSelection = true) { x, r -> SampleOps.fadeOut(x, r) } },
                    "Mono" to { vm.editorApply { x, _ -> SampleOps.toMono(x) } },
                )

                Label("Level")
                ToolRow(
                    "Normalize" to { vm.editorApply(keepSelection = true) { x, _ -> SampleOps.normalize(x) } },
                    "Gain -3 dB" to { vm.editorApply(keepSelection = true) { x, r -> SampleOps.gainDb(x, -3f, r) } },
                    "Gain +3 dB" to { vm.editorApply(keepSelection = true) { x, r -> SampleOps.gainDb(x, 3f, r) } },
                )

                Label("Pitch in semitones")
                ToolRow(
                    "-12" to { vm.editorApply { x, _ -> SampleOps.repitch(x, -12.0) } },
                    "-1" to { vm.editorApply { x, _ -> SampleOps.repitch(x, -1.0) } },
                    "+1" to { vm.editorApply { x, _ -> SampleOps.repitch(x, 1.0) } },
                    "+12" to { vm.editorApply { x, _ -> SampleOps.repitch(x, 12.0) } },
                )
                Text(
                    "Pitch changes the speed like a sampler: up is shorter, down is longer.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                )

                Label("Save")
                val src = e.source as? EditorSource.Device
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeButton("Replace on device", { vm.editorReplace() }, Modifier.weight(1f), enabled = online && src != null && !tooLong)
                    TeButton("Save as new", { vm.editorSaveNew() }, Modifier.weight(1f), enabled = online && !tooLong)
                }
                if (src != null) {
                    Text(
                        "Replace keeps slot ${"%03d".format(src.slot)} and its settings. The old version goes to the trash.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Gap(6)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TeButton("Rename", { renaming = true }, Modifier.weight(1f))
                    TeButton("Export WAV", { saveWav.launch(e.name.ifEmpty { "sample" } + ".wav") }, Modifier.weight(1f))
                }
            }

            Label("Open")
            TeButton("Open a file from your phone", { openFile.launch(arrayOf("audio/*", "application/octet-stream")) }, Modifier.fillMaxWidth())
            Text(
                "To edit a sound on the K.O. II, open it on the Sounds tab and choose Edit sample.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
            )
            Gap(16)
        }
    }

    if (renaming) {
        var text by remember { mutableStateOf(e.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            confirmButton = { TextButton({ vm.editorRename(text); renaming = false }) { Text("OK") } },
            dismissButton = { TextButton({ renaming = false }) { Text("Cancel") } },
            title = { Text("Name") },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, singleLine = true)
                    Text("Shown as: " + de.koii.protocol.Names.normalizeSlotName(text), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}

@Composable
private fun ToolRow(vararg tools: Pair<String, () -> Unit>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tools.forEach { (label, action) -> TeButton(label, action, Modifier.weight(1f)) }
    }
}
