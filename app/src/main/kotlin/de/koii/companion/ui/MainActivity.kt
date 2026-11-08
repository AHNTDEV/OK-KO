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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import de.koii.companion.data.Link

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
        setContent {
            val themeId by vm.g.prefs.theme.collectAsState()
            val palette = Palettes.byId(themeId)
            KoiiTheme(palette) {
                val view = LocalView.current
                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !palette.dark
                        isAppearanceLightNavigationBars = !palette.dark
                    }
                }
                Root(vm)
            }
        }
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
        TopBar(vm)
        Box(Modifier.weight(1f)) {
            when (val s = sub) {
                Sub.Device -> DeviceScreen(vm)
                Sub.Settings -> SettingsScreen(vm)
                Sub.Upload -> UploadScreen(vm)
                Sub.Trash -> TrashScreen(vm)
                is Sub.PadAssign -> PadAssignScreen(vm, s)
                is Sub.SoundDetail -> SoundDetailScreen(vm, s.id)
                null -> when (tab) {
                    Tab.PADS -> PadsScreen(vm)
                    Tab.SOUNDS -> SoundsScreen(vm)
                    Tab.KEYS -> KeysScreen(vm)
                    Tab.REC -> RecorderScreen(vm)
                    Tab.LIBRARY -> LibraryScreen(vm)
                }
            }
        }

        if (transfer.active || transfer.queued > 0) {
            Column(Modifier.fillMaxWidth().background(Ink.Surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    transfer.label.uppercase() + if (transfer.queued > 1) "  (+${transfer.queued - 1})" else "",
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp),
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
        TabBar(Tab.entries.map { it.label }, if (sub == null) tab.ordinal else -1) { vm.select(Tab.entries[it]) }
    }
}

@Composable
private fun TopBar(vm: KoiiViewModel) {
    val link by vm.g.session.link.collectAsState()
    val (label, color) = when (val l = link) {
        is Link.Online -> "K.O. II · FW ${l.info.greet.osVersion}" to Ink.group(1)
        is Link.Connecting -> "Connecting" to Ink.group(2)
        Link.NeedsPermission -> "USB permission" to Ink.group(2)
        is Link.Failed -> "Error" to Ink.group(3)
        Link.Offline -> "Offline" to Ink.Dim
    }
    Row(
        Modifier.fillMaxWidth().background(Ink.Surface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("KO II", style = MaterialTheme.typography.titleMedium, color = Ink.Accent)
        Text(" COMPANION", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Chip(label, color = color, onClick = { vm.open(Sub.Device) })
        Chip("Settings", color = Ink.Dim, modifier = Modifier.padding(start = 8.dp), onClick = { vm.open(Sub.Settings) })
    }
}
