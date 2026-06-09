package com.ykfj.inventory.data.local.backup

import android.content.Context
import android.content.Intent

object BackupRestoreHelper {
    /**
     * Relaunches the app from scratch. Required after [BackupManager.restoreFromZip]
     * because the Room singleton has been closed and Hilt's graph won't reissue it
     * within the same process.
     */
    fun restartProcess(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (intent != null) context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
