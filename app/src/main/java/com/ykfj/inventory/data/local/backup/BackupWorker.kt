package com.ykfj.inventory.data.local.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Periodic worker that runs the auto DB-only backup. Scheduled by
 * [scheduleDaily] at app start; no-op if already enqueued.
 *
 * Auto backups go to internal storage and rotate to keep [BackupManager.AUTO_KEEP].
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (val r = backupManager.createAutoBackup()) {
            is BackupManager.CreateResult.Success -> {
                Log.i(TAG, "Auto backup written: ${r.displayName} (${r.sizeBytes} bytes)")
                Result.success()
            }
            is BackupManager.CreateResult.Failed -> {
                Log.w(TAG, "Auto backup failed: ${r.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "BackupWorker"
        private const val UNIQUE_NAME = "ykfj_auto_backup_daily"

        /** Idempotent — call from app start. */
        fun scheduleDaily(context: Context) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatInterval = 1,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            ).setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
