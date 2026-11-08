package de.koii.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import de.koii.companion.KoiiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Foreground service that exists while TransferManager has work; shows progress as a notification. */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val first = build("KO II", "Preparing…", 0, 0)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, first, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, first)
        }
        if (watcher == null) {
            val manager = (application as KoiiApp).graph.transfers
            watcher = scope.launch {
                manager.state.collect { s ->
                    if (!s.active && s.queued == 0) {
                        stopSelf()
                    } else {
                        val nm = getSystemService(NotificationManager::class.java)
                        nm.notify(NOTIF_ID, build("KO II", s.label.ifEmpty { "Transfer" }, s.done, s.total))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(title: String, text: String, done: Long, total: Long): Notification {
        val b = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
        if (total > 0) b.setProgress(100, (done * 100 / total).toInt(), false) else b.setProgress(0, 0, true)
        return b.build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Transfers", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val CHANNEL = "transfers"
        const val NOTIF_ID = 1
    }
}
