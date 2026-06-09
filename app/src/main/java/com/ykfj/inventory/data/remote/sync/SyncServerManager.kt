package com.ykfj.inventory.data.remote.sync

import android.util.Log
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.entity.AppSettingsEntity
import com.ykfj.inventory.data.local.image.ImageStorageManager
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncServerManager @Inject constructor(
    private val db: YkfjDatabase,
    private val imageStorageManager: ImageStorageManager,
    private val nsdRegistrar: NsdRegistrar,
) {
    private var engine: ApplicationEngine? = null

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    val port = SERVER_PORT

    /**
     * Starts the Ktor embedded server. Must be called from a coroutine (reads JWT secret from DB).
     * Idempotent — calling start() while already running is a no-op.
     */
    suspend fun start() {
        if (_isRunning.value) return

        val secret = getOrCreateJwtSecret()
        val jwtConfig = JwtConfig(secret)
        val deviceId = getOrCreateDeviceId()

        engine = embeddedServer(Netty, port = SERVER_PORT) {
            configureSyncServer(
                db = db,
                imageStorageManager = imageStorageManager,
                jwtConfig = jwtConfig,
                deviceId = deviceId,
            )
        }.also { it.start(wait = false) }

        nsdRegistrar.register(deviceId, SERVER_PORT)
        _isRunning.value = true
        Log.i(TAG, "Sync server started on port $SERVER_PORT (device=$deviceId)")
    }

    /** Stops the server gracefully. Safe to call even if not running. */
    fun stop() {
        nsdRegistrar.unregister()
        engine?.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
        engine = null
        _isRunning.value = false
        Log.i(TAG, "Sync server stopped")
    }

    private suspend fun getOrCreateJwtSecret(): String {
        return db.appSettingsDao().getValue(KEY_JWT_SECRET) ?: UUID.randomUUID().toString().also {
            db.appSettingsDao().upsert(AppSettingsEntity(key = KEY_JWT_SECRET, value = it))
        }
    }

    private suspend fun getOrCreateDeviceId(): String {
        return db.appSettingsDao().getValue(KEY_DEVICE_ID) ?: "tablet-${UUID.randomUUID()}".also {
            db.appSettingsDao().upsert(AppSettingsEntity(key = KEY_DEVICE_ID, value = it))
        }
    }

    companion object {
        const val SERVER_PORT = 8080
        private const val KEY_JWT_SECRET = "jwt_secret"
        private const val KEY_DEVICE_ID = "device_id"
        private const val TAG = "SyncServerManager"
    }
}
