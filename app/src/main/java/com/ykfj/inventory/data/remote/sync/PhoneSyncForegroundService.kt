package com.ykfj.inventory.data.remote.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service for the phone role.
 *
 * Keeps NSD discovery alive and runs a periodic sync loop every [SYNC_INTERVAL_MS].
 * The notification reflects live sync status (syncing / last synced / error).
 *
 * Started by [YkfjApp] on boot when the device role is PHONE, and toggled from
 * [com.ykfj.inventory.ui.settings.SettingsViewModel] when the user switches roles.
 */
@AndroidEntryPoint
class PhoneSyncForegroundService : Service() {

    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var nsdDiscovery: NsdDiscovery

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncLoop: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting to tablet…"))

        nsdDiscovery.startDiscovery()

        serviceScope.launch {
            syncManager.status.collect { status ->
                val text = when {
                    status.isSyncing -> "Syncing…"
                    status.lastError != null -> "Sync error: ${status.lastError}"
                    status.lastSyncTime > 0L ->
                        "Last synced ${DateUtils.getRelativeTimeSpanString(status.lastSyncTime)}" +
                            if (status.pendingCount > 0) " · ${status.pendingCount} pending" else ""
                    else -> "Waiting for tablet…"
                }
                updateNotification(text)
            }
        }

        syncLoop = serviceScope.launch {
            // Exponential backoff on repeated failure so the phone doesn't
            // hammer an offline tablet every 5 minutes forever (battery + LAN).
            // Reset to the baseline interval on the first successful sync.
            var delayMs = SYNC_INTERVAL_MS
            while (true) {
                syncManager.sync()
                val status = syncManager.status.value
                delayMs = if (status.lastError != null) {
                    (delayMs * 2L).coerceAtMost(MAX_BACKOFF_MS)
                } else {
                    SYNC_INTERVAL_MS
                }
                delay(delayMs)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        syncLoop?.cancel()
        nsdDiscovery.stopDiscovery()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "YKFJ Phone Sync",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps phone inventory data in sync with the tablet"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YKFJ Sync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setSilent(true)
            .build()

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        private const val CHANNEL_ID = "ykfj_phone_sync"
        private const val NOTIFICATION_ID = 1002
        const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes (baseline)
        /** Backoff cap — never wait longer than this between sync attempts. */
        const val MAX_BACKOFF_MS = 60 * 60 * 1000L // 60 minutes
    }
}
