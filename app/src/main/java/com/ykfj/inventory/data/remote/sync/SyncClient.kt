package com.ykfj.inventory.data.remote.sync

import android.util.Log
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.entity.AppSettingsEntity
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP client for the phone-side sync.  Resolves the tablet address via
 * [ConnectionResolver] before every call so the host can change between
 * NSD (WiFi) and Tailscale (remote) transparently.
 *
 * The JWT token obtained on login is persisted in [AppSettingsDao] under
 * [KEY_JWT_TOKEN] and re-attached to every authenticated request.  If the
 * tablet returns 401, callers should re-login and retry.
 *
 * All methods return `null` instead of throwing on network or parse errors;
 * the caller ([SyncManager]) decides how to handle unavailability.
 */
@Singleton
class SyncClient @Inject constructor(
    private val connectionResolver: ConnectionResolver,
    private val db: YkfjDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }

    private suspend fun baseUrl(): String? {
        val conn = connectionResolver.resolve() ?: return null
        return "http://${conn.host}:${conn.port}"
    }

    private suspend fun token(): String? = db.appSettingsDao().getValue(KEY_JWT_TOKEN)

    /**
     * Authenticates with the tablet. On success, stores the JWT for future calls.
     * Returns a [Result] so callers can inspect the specific failure reason
     * (connection refused vs. wrong credentials vs. timeout).
     */
    suspend fun login(username: String, password: String): Result<LoginResponse> {
        val base = baseUrl()
            ?: return Result.failure(IllegalStateException("No tablet IP configured — enter an address in Settings"))
        return runCatching {
            httpClient.post("$base/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }.body<LoginResponse>().also { resp ->
                db.appSettingsDao().upsert(AppSettingsEntity(key = KEY_JWT_TOKEN, value = resp.token))
            }
        }.also { if (it.isFailure) Log.w(TAG, "login failed: ${it.exceptionOrNull()?.message}") }
    }

    /** Pulls all entities changed on the tablet since [since] (epoch ms). */
    suspend fun getChanges(since: Long): ChangesPayload? {
        val base = baseUrl() ?: return null
        val token = token() ?: return null
        return runCatching {
            httpClient.get("$base/api/sync/changes") {
                parameter("since", since)
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<ChangesPayload>()
        }.getOrElse { Log.w(TAG, "getChanges failed: ${it.message}"); null }
    }

    /** Pushes locally-created changes to the tablet for last-write-wins merge. */
    suspend fun push(payload: ChangesPayload): PushResponse? {
        val base = baseUrl() ?: return null
        val token = token() ?: return null
        return runCatching {
            httpClient.post("$base/api/sync/push") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(payload)
            }.body<PushResponse>()
        }.getOrElse { Log.w(TAG, "push failed: ${it.message}"); null }
    }

    /**
     * Downloads a single product image.
     *
     * @param imageId the UUID stored in [ProductImageEntity.image_id]
     * @param type    "thumb" for the 200×200 thumbnail, "full" for the ~1024px version
     */
    suspend fun downloadImageBytes(imageId: String, type: String): ByteArray? {
        val base = baseUrl() ?: return null
        val token = token() ?: return null
        return runCatching {
            httpClient.get("$base/api/images/$imageId") {
                parameter("type", type)
                header(HttpHeaders.Authorization, "Bearer $token")
            }.body<ByteArray>()
        }.getOrElse { Log.w(TAG, "downloadImage $imageId/$type failed: ${it.message}"); null }
    }

    /**
     * Uploads a single product image variant to the tablet.
     *
     * Caller must have first pushed the [ProductImageEntity] row via /sync/push
     * — the tablet refuses uploads for which no metadata row exists.
     */
    suspend fun uploadImageBytes(imageId: String, type: String, bytes: ByteArray): Boolean {
        val base = baseUrl() ?: return false
        val token = token() ?: return false
        return runCatching {
            httpClient.post("$base/api/images/$imageId") {
                parameter("type", type)
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Image.JPEG)
                setBody(bytes)
            }
            true
        }.getOrElse { Log.w(TAG, "uploadImage $imageId/$type failed: ${it.message}"); false }
    }

    companion object {
        const val KEY_JWT_TOKEN = "jwt_token"
        private const val TAG = "SyncClient"
    }
}
