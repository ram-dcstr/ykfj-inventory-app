package com.ykfj.inventory

import android.app.Application
import android.content.Intent
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ykfj.inventory.data.local.AppSettingKeys
import com.ykfj.inventory.data.local.backup.BackupWorker
import com.ykfj.inventory.data.local.db.DatabaseSeeder
import com.ykfj.inventory.data.local.db.dao.AppSettingsDao
import com.ykfj.inventory.data.remote.sync.PhoneSyncForegroundService
import com.ykfj.inventory.data.remote.sync.SyncForegroundService
import com.ykfj.inventory.data.repository.DeviceRoleManager
import com.ykfj.inventory.domain.sync.DeviceRole
import com.ykfj.inventory.domain.usecase.activitylog.CleanupActivityLogsUseCase
import com.ykfj.inventory.ui.auth.IdleTimeout
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application entry point. Hilt generates the application component from this
 * class and injects [DatabaseSeeder] so the default admin account is created
 * on first launch.
 *
 * Implements [Configuration.Provider] so WorkManager can resolve `@HiltWorker`
 * workers (currently [BackupWorker]) — without this, the worker can't be
 * instantiated because Hilt needs to inject its dependencies.
 *
 * Seeding runs on an application-scoped IO coroutine — it is idempotent, so
 * launching fire-and-forget from `onCreate` is safe even if the process is
 * killed mid-seed.
 */
@HiltAndroidApp
class YkfjApp : Application(), Configuration.Provider {

    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var deviceRoleManager: DeviceRoleManager
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var cleanupActivityLogs: CleanupActivityLogsUseCase
    @Inject lateinit var sessionManager: SessionManager
    @Inject lateinit var appSettingsDao: AppSettingsDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { databaseSeeder.seedIfEmpty() }
        appScope.launch { startRoleServices() }
        appScope.launch { cleanupActivityLogs() }
        appScope.launch { loadSessionTimeout() }
        BackupWorker.scheduleDaily(this)
    }

    private suspend fun loadSessionTimeout() {
        val persisted = appSettingsDao.getValue(AppSettingKeys.SESSION_TIMEOUT)
        sessionManager.setIdleTimeout(IdleTimeout.fromName(persisted))
    }

    private suspend fun startRoleServices() {
        when (deviceRoleManager.getRole()) {
            DeviceRole.TABLET ->
                startForegroundService(Intent(this, SyncForegroundService::class.java))
            DeviceRole.PHONE ->
                startForegroundService(Intent(this, PhoneSyncForegroundService::class.java))
        }
    }
}
