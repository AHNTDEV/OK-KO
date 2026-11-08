package de.koii.companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.koii.companion.data.Link

@Composable
fun DeviceScreen(vm: KoiiViewModel) {
    val g = vm.g
    val link by g.session.link.collectAsState()
    val storage by g.session.storage.collectAsState()
    val log by g.session.log.collectAsState()

    Screen("Device", actions = { TeButton("Back", { vm.back() }) }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val status = when (val l = link) {
                Link.Offline -> "No device. Connect the K.O. II with USB-C."
                Link.NeedsPermission -> "USB permission needed."
                is Link.Connecting -> "Connecting to ${l.name}…"
                is Link.Online -> "Online"
                is Link.Failed -> "Error: ${l.message}"
            }
            Text(status, style = MaterialTheme.typography.titleMedium)
            Gap()

            when (val l = link) {
                is Link.Online -> {
                    val gr = l.info.greet
                    KeyValue("Product", gr.product.ifEmpty { l.usbName })
                    KeyValue("SKU", gr.sku.ifEmpty { l.info.identity.sku })
                    KeyValue("Serial", gr.serial.ifEmpty { "–" })
                    KeyValue("Firmware", gr.osVersion.ifEmpty { "–" })
                    KeyValue("Mode", gr.mode.ifEmpty { "–" })
                    KeyValue("Chunk size", "${l.info.chunkSize} B")
                    storage?.let {
                        KeyValue("Free", "${it.freeBytes / 1024 / 1024} MB of ${it.totalBytes / 1024 / 1024} MB")
                    }
                    KeyValue("USB audio", if (l.usbAudioAvailable) "available" else "needs FW 2.5+")
                }
                else -> Unit
            }

            Gap()
            if (link !is Link.Online) {
                TeButton("Scan again", onClick = { g.session.retry() }, modifier = Modifier.fillMaxWidth())
            }

            Label("Device log")
            if (log.isEmpty()) {
                Text("No debug output yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                log.takeLast(40).forEach { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 1.dp)) }
            }
        }
    }
}
