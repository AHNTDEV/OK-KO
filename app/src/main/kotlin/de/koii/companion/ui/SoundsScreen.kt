package de.koii.companion.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.koii.companion.data.Link
import de.koii.companion.data.SoundRepository

private const val PAGE = 48 // 12 × 4 grid

@Composable
fun SoundsScreen(vm: KoiiViewModel) {
    val g = vm.g
    val link by g.session.link.collectAsState()
    val slots by g.sounds.slots.collectAsState()
    val names by g.sounds.names.collectAsState()

    var page by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(0) }       // 0 = none
    var moveFrom by rememberSaveable { mutableStateOf(0) }       // 0 = not moving
    var multi by remember { mutableStateOf(setOf<Int>()) }       // long-press selection
    var confirmDelete by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf(0) }

    val searching = query.isNotBlank()
    val pages = (SoundRepository.SLOT_COUNT + PAGE - 1) / PAGE
    val first = page * PAGE + 1
    val visible: List<Int> = if (searching) {
        slots.keys.sorted().filter { id ->
            "%03d".format(id).contains(query.trim()) || (names[id] ?: entryName(slots[id])).contains(query.trim(), ignoreCase = true)
        }.take(200)
    } else {
        (first until minOf(first + PAGE, SoundRepository.SLOT_COUNT + 1)).toList()
    }

    LaunchedEffect(page, searching, slots.keys) {
        vm.loadNames(if (searching) slots.keys else visible.filter { it in slots })
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) vm.stage(uris)
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")) { uri: Uri? ->
        if (uri != null && pendingDownload != 0) vm.downloadSound(pendingDownload, uri)
    }
    val exportTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null && multi.isNotEmpty()) { vm.exportSounds(multi.sorted(), uri); multi = emptySet() }
    }

    Screen("Sounds", actions = { TeButton("Trash", onClick = { vm.open(Sub.Trash) }) }) {
        if (link !is Link.Online) {
            Text("Connect the K.O. II with a USB-C data cable. Its sounds appear here, ready to search, edit and sort.", style = MaterialTheme.typography.bodyMedium)
            return@Screen
        }

        OutlinedTextField(
            query, { query = it }, label = { Text("Search by name or slot") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Gap(6)
        if (!searching) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeButton("<", { if (page > 0) page-- }, enabled = page > 0)
                Box(Modifier.weight(1f).padding(top = 12.dp)) {
                    Text(
                        "Slots %03d to %03d, ${slots.size} used".format(first, visible.last()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TeButton(">", { if (page < pages - 1) page++ }, enabled = page < pages - 1)
            }
        } else {
            Text("${visible.size} match" + if (visible.size == 1) "" else "es", style = MaterialTheme.typography.bodySmall)
        }
        Gap(6)

        if (moveFrom != 0) {
            Text(
                "Moving ${"%03d".format(moveFrom)}. Tap a target slot; an occupied slot swaps places.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp),
            )
        } else if (multi.isEmpty()) {
            Text("Press and hold a sound to select several.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 6.dp))
        }

        // 4 columns × 12 rows = one page of 48 slots
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(visible, key = { it }) { slot ->
                val entry = slots[slot]
                TeCell(
                    modifier = Modifier.aspectRatio(1.35f),
                    selected = slot == selected || slot == moveFrom || slot in multi,
                    filled = entry != null,
                    onClick = {
                        when {
                            multi.isNotEmpty() -> if (entry != null) multi = if (slot in multi) multi - slot else multi + slot
                            moveFrom != 0 -> { vm.moveOrSwap(moveFrom, slot); selected = slot; moveFrom = 0 }
                            else -> selected = if (selected == slot) 0 else slot
                        }
                    },
                    onLongClick = { if (entry != null) { multi = multi + slot; selected = 0; moveFrom = 0 } },
                ) { fg ->
                    Column {
                        Text("%03d".format(slot), style = MaterialTheme.typography.labelSmall, color = fg)
                        if (entry != null) {
                            Text(
                                names[slot] ?: entry.name, style = MaterialTheme.typography.bodySmall.copy(color = fg),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Gap(8)
        if (multi.isNotEmpty()) {
            Text("${multi.size} selected", style = MaterialTheme.typography.labelLarge)
            Gap(4)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Delete", { confirmDelete = true }, Modifier.weight(1f))
                TeButton("Export", { exportTree.launch(null) }, Modifier.weight(1f))
                TeButton("Clear", { multi = emptySet() }, Modifier.weight(1f))
            }
        } else {
            val sel = selected
            val occupied = sel != 0 && sel in slots
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Play", { vm.preview(sel) }, Modifier.weight(1f), enabled = occupied)
                TeButton("Move", { moveFrom = if (moveFrom == 0) sel else 0 }, Modifier.weight(1f), selected = moveFrom != 0, enabled = occupied)
                TeButton("Edit", { vm.openDetail(sel) }, Modifier.weight(1f), enabled = occupied)
            }
            Gap(6)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton("Save", { pendingDownload = sel; save.launch((names[sel] ?: "sound") + ".wav") }, Modifier.weight(1f), enabled = occupied)
                TeButton("Delete", { confirmDelete = true }, Modifier.weight(1f), enabled = occupied)
                TeButton("Upload", { pick.launch(arrayOf("audio/*", "application/octet-stream")) }, Modifier.weight(1f))
            }
        }
        Gap(8)
    }

    if (confirmDelete && (multi.isNotEmpty() || selected != 0)) {
        val ids = if (multi.isNotEmpty()) multi.sorted() else listOf(selected)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton({ vm.deleteToTrash(ids); confirmDelete = false; selected = 0; multi = emptySet() }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
            title = { Text(if (ids.size == 1) "Delete ${"%03d".format(ids[0])}?" else "Delete ${ids.size} sounds?") },
            text = { Text("A copy is kept in the app trash and can be restored.") },
        )
    }
}

private fun entryName(e: de.koii.protocol.FileEntry?): String = e?.name.orEmpty()

@Composable
fun UploadScreen(vm: KoiiViewModel) {
    val staged by vm.staged.collectAsState()
    val slots by vm.g.sounds.slots.collectAsState()
    var start by rememberSaveable { mutableStateOf("1") }

    Screen("Upload", actions = { TeButton("Back", { vm.clearStaged(); vm.back() }) }) {
        if (staged.isEmpty()) Text("Preparing files…", style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(staged, key = { it.key }) { s ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(s.fileName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sound = s.sound
                    if (sound != null) {
                        Text(
                            "Becomes ${s.displayName}, %.1f s, %s, %d channel%s".format(
                                sound.seconds, if (sound.fastPath) "sent as is" else "converted to 46875 Hz", sound.channels, if (sound.channels == 1) "" else "s",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text("✕ ${s.error}", style = MaterialTheme.typography.bodySmall)
                    }
                    TeButton("Remove", { vm.unstage(s.key) }, Modifier.padding(top = 6.dp))
                }
                Rule()
            }
        }
        Gap()
        val startSlot = start.toIntOrNull()?.coerceIn(1, SoundRepository.SLOT_COUNT) ?: 1
        val free = vm.g.sounds.freeSlots(startSlot)
        val sendable = staged.count { it.sound != null }
        Text("Next free slot from $startSlot: ${free.firstOrNull()?.let { "%03d".format(it) } ?: "none"}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(start, { start = it.filter(Char::isDigit).take(3) }, label = { Text("Start slot") }, singleLine = true)
        Gap()
        TeButton(
            "Send $sendable file(s)", { vm.sendStaged(startSlot) }, Modifier.fillMaxWidth(),
            enabled = sendable > 0 && free.size >= sendable && slots.size + sendable <= SoundRepository.SLOT_COUNT,
        )
        Gap()
    }
}

@Composable
fun TrashScreen(vm: KoiiViewModel) {
    val items by vm.g.trash.items.collectAsState()
    val fmt = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT) }

    Screen("Trash", actions = { TeButton("Back", { vm.back() }) }) {
        if (items.isEmpty()) Text("Empty.", style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.weight(1f)) {
            items(items.sortedByDescending { it.deletedAt }, key = { it.id }) { t ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text("${"%03d".format(t.slot)}  ${t.name}", style = MaterialTheme.typography.bodyLarge)
                    Text("Deleted ${fmt.format(java.util.Date(t.deletedAt))}, ${t.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TeButton("Restore", { vm.restore(t) })
                        TeButton("Remove", { vm.purge(t) })
                    }
                }
                Rule()
            }
        }
        if (items.isNotEmpty()) {
            Gap()
            TeButton("Empty trash", { vm.emptyTrash() }, Modifier.fillMaxWidth())
            Gap()
        }
    }
}
