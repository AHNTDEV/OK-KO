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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.koii.companion.data.Link
import de.koii.protocol.FileEntry

@Composable
fun LibraryScreen(vm: KoiiViewModel) {
    val link by vm.g.session.link.collectAsState()
    val projects by vm.projects.collectAsState()
    val plan by vm.restorePlan.collectAsState()
    val online = link is Link.Online

    var withSounds by remember { mutableStateOf(true) }
    var withProjects by remember { mutableStateOf(true) }
    var pendingProject by remember { mutableStateOf<FileEntry?>(null) }

    LaunchedEffect(online) { if (online) vm.refreshProjects() }

    val backupSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) vm.backupTo(uri, withSounds, withProjects)
    }
    val restoreOpener = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.inspectBackup(uri)
    }
    val tarSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-tar")) { uri: Uri? ->
        val p = pendingProject
        if (uri != null && p != null) vm.downloadProject(p, uri)
    }

    Screen("Library", actions = {
        TeButton("Trash", { vm.open(Sub.Trash) })
        TeButton("Refresh", { vm.refreshProjects() }, Modifier.padding(start = 6.dp), enabled = online)
    }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Label("Projects")
            if (!online) Text("Connect the K.O. II with a USB-C data cable to see its projects and back them up.", style = MaterialTheme.typography.bodyMedium)
            projects.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text("${p.size / 1024} KB", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                    TeButton("Tar", { pendingProject = p; tarSaver.launch("${p.name}.tar") })
                }
                Rule()
            }

            Label("Backup")
            Check("Sounds (as WAV)", withSounds) { withSounds = it }
            Check("Projects (as tar)", withProjects) { withProjects = it }
            Gap(4)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TeButton(
                    "Backup", { backupSaver.launch("ko2-backup-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date()) + ".zip") },
                    Modifier.weight(1f), enabled = online && (withSounds || withProjects),
                )
                TeButton("Restore", { restoreOpener.launch(arrayOf("application/zip", "application/octet-stream")) }, Modifier.weight(1f), enabled = online)
            }
            Gap(24)
        }
    }

    val current = plan
    if (current != null) {
        val (_, meta) = current
        var overwrite by remember(current) { mutableStateOf(false) }
        var rSounds by remember(current) { mutableStateOf(meta.sounds.isNotEmpty()) }
        var rProjects by remember(current) { mutableStateOf(meta.projects.isNotEmpty()) }
        val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.ROOT).format(java.util.Date(meta.created))
        AlertDialog(
            onDismissRequest = { vm.cancelRestore() },
            confirmButton = {
                TextButton({ vm.restoreBackup(overwrite, rSounds, rProjects) }, enabled = rSounds || rProjects) { Text("Restore") }
            },
            dismissButton = { TextButton({ vm.cancelRestore() }) { Text("Cancel") } },
            title = { Text("Restore backup") },
            text = {
                Column {
                    Text("Made $date, firmware ${meta.osVersion}", style = MaterialTheme.typography.bodySmall)
                    Check("${meta.sounds.size} sounds", rSounds && meta.sounds.isNotEmpty()) { rSounds = it && meta.sounds.isNotEmpty() }
                    Check("${meta.projects.size} projects", rProjects && meta.projects.isNotEmpty()) { rProjects = it && meta.projects.isNotEmpty() }
                    Check("Replace what is already on the device", overwrite) { overwrite = it }
                    Text(
                        if (overwrite) "Occupied slots and existing projects will be overwritten."
                        else "Occupied slots and existing projects are skipped.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
    }
}
