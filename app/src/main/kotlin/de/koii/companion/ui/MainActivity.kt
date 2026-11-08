package de.koii.companion.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private val vm: KoiiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { KoiiTheme { Root(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.g.usb.scan()
    }

    override fun onResume() {
        super.onResume()
        vm.g.usb.scan()
    }
}

@Composable
private fun Root(vm: KoiiViewModel) {
    val tab by vm.tab.collectAsState()
    val sub by vm.sub.collectAsState()
    val notice by vm.notice.collectAsState()
    val transfer by vm.g.transfers.state.collectAsState()

    BackHandler(enabled = sub != null) { vm.back() }

    Column(Modifier.fillMaxSize().background(Ink.Bg).systemBarsPadding().imePadding()) {
        Box(Modifier.weight(1f)) {
            when (val s = sub) {
                Sub.Upload -> UploadScreen(vm)
                Sub.Trash -> TrashScreen(vm)
                is Sub.PadAssign -> PadAssignScreen(vm, s)
                null -> when (tab) {
                    Tab.DEVICE -> DeviceScreen(vm)
                    Tab.SOUNDS -> SoundsScreen(vm)
                    Tab.PADS -> PadsScreen(vm)
                    Tab.PROJECTS -> ProjectsScreen(vm)
                    Tab.REC -> RecorderScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
            }
        }

        if (transfer.active || transfer.queued > 0) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    transfer.label.uppercase() + if (transfer.queued > 1) "  (+${transfer.queued - 1})" else "",
                    style = MaterialTheme.typography.labelSmall,
                )
                Meter(transfer.fraction)
            }
        }
        val msg = notice ?: transfer.lastError
        if (msg != null) {
            Row(Modifier.fillMaxWidth().background(Ink.Surface).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(msg, style = MaterialTheme.typography.bodySmall, color = Ink.Fg, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                TeButton("OK", { vm.say(null) }, Modifier.padding(8.dp))
            }
        }
        TabBar(Tab.entries.map { it.label }, tab.ordinal) { vm.select(Tab.entries[it]) }
    }
}
