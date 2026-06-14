package com.ykfj.inventory.data.remote.sync

import android.util.Base64
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
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncServerManager @Inject constructor(
    private val db: YkfjDatabase,
    private val imageStorageManager: ImageStorageManager,
    private val nsdRegistrar: NsdRegistrar,
    private val secretStore: KeystoreSecretStore,
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

    /**
     * Returns the JWT signing secret, kept Keystore-wrapped at rest.
     *
     * Three code paths:
     *  1. Encrypted value present → decrypt and return (the steady state).
     *  2. Legacy plaintext value present (older installs) → wrap it, write the
     *     ciphertext to `KEY_JWT_SECRET_ENC`, delete the plaintext row, return.
     *     This is the one-time migration; idempotent on re-runs.
     *  3. Nothing present (fresh install) → generate 256 bits of secure random,
     *     wrap it, persist the ciphertext.
     *
     * If decryption fails (corrupt ciphertext, Keystore key wiped by clear-data),
     * we fall through to (3) so the server can still come up; phones holding
     * old tokens will have to re-login.
     */
    private suspend fun getOrCreateJwtSecret(): String {
        val dao = db.appSettingsDao()
        dao.getValue(KEY_JWT_SECRET_ENC)?.let { encoded ->
            runCatching { secretStore.decrypt(encoded) }
                .onSuccess { return it }
                .onFailure {
                    Log.w(TAG, "Stored JWT secret could not be decrypted — regenerating", it)
                }
        }
        dao.getValue(KEY_JWT_SECRET_LEGACY)?.let { legacy ->
            val wrapped = secretStore.encrypt(legacy)
            dao.upsert(AppSettingsEntity(key = KEY_JWT_SECRET_ENC, value = wrapped))
            dao.delete(KEY_JWT_SECRET_LEGACY)
            Log.i(TAG, "Migrated JWT secret from plaintext to Keystore-wrapped storage")
            return legacy
        }
        val freshBytes = ByteArray(SECRET_BYTE_LENGTH).also { SecureRandom().nextBytes(it) }
        val secret = Base64.encodeToString(freshBytes, Base64.NO_WRAP or Base64.URL_SAFE)
        val wrapped = secretStore.encrypt(secret)
        dao.upsert(AppSettingsEntity(key = KEY_JWT_SECRET_ENC, value = wrapped))
        return secret
    }

    private suspend fun getOrCreateDeviceId(): String {
        return db.appSettingsDao().getValue(KEY_DEVICE_ID) ?: "tablet-${UUID.randomUUID()}".also {
            db.appSettingsDao().upsert(AppSettingsEntity(key = KEY_DEVICE_ID, value = it))
        }
    }

    companion object {
        const val SERVER_PORT = 8080
        /** Key for the Keystore-wrapped JWT signing secret. */
        private const val KEY_JWT_SECRET_ENC = "jwt_secret_enc"
        /** Old key — read once during migration, then deleted. */
        private const val KEY_JWT_SECRET_LEGACY = "jwt_secret"
        private const val KEY_DEVICE_ID = "device_id"
        /** 32 bytes = 256-bit secret for HMAC-SHA256. */
        private const val SECRET_BYTE_LENGTH = 32
        private const val TAG = "SyncServerManager"
    }
}
