package com.ykfj.inventory.data.remote.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that keeps the Ktor sync server alive when the app is
 * backgrounded or the screen is off. Starts automatically on app launch
 * (see [com.ykfj.inventory.YkfjApp]) and survives system backgrounding.
 *
 * Shows a persistent "YKFJ Server Running" notification — required for
 * foreground services on API 31+.
 */
@AndroidEntryPoint
class SyncForegroundService : Service() {

    @Inject lateinit var syncServerManager: SyncServerManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        serviceScope.launch { syncServerManager.start() }
        return START_STICKY
    }

    override fun onDestroy() {
        syncServerManager.stop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "YKFJ Sync Server",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Keeps the tablet sync server running for phone access"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("YKFJ Server Running")
        .setContentText("Tablet is reachable on port ${syncServerManager.port}")
        .setSmallIcon(android.R.drawable.ic_menu_share)
        .setOngoing(true)
        .setSilent(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "ykfj_sync_server"
        private const val NOTIFICATION_ID = 1001
    }
}
