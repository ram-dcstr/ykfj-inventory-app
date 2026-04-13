package com.ykfj.inventory

import android.app.Application
import com.ykfj.inventory.data.local.db.DatabaseSeeder
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
 * Seeding runs on an application-scoped IO coroutine — it is idempotent, so
 * launching fire-and-forget from `onCreate` is safe even if the process is
 * killed mid-seed.
 */
@HiltAndroidApp
class YkfjApp : Application() {

    @Inject
    lateinit var databaseSeeder: DatabaseSeeder

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch { databaseSeeder.seedIfEmpty() }
    }
}
