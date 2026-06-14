package com.ykfj.inventory.data.remote.sync

import android.util.Log
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.image.ImageStorageManager
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

/**
 * Configures the Ktor [Application] with all YKFJ sync server plugins and routes.
 *
 * Called from [SyncServerManager] when the embedded Netty engine is started.
 * Tablet is source of truth; all phones authenticate here and sync via delta pulls.
 */
fun Application.configureSyncServer(
    db: YkfjDatabase,
    imageStorageManager: ImageStorageManager,
    jwtConfig: JwtConfig,
    deviceId: String,
) {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }

    install(Authentication) {
        jwt("jwt-auth") {
            realm = jwtConfig.realm
            verifier(jwtConfig.verifier)
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
            }
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            // Log the full detail server-side for debugging, but return a
            // generic body so we don't leak schema / SQL / file-path info to
            // an attacker who can reach the LAN port.
            Log.e("SyncServer", "Unhandled exception on ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal server error"),
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(HttpStatusCode.NotFound, mapOf("error" to "Not found"))
        }
    }

    // One throttle per server lifecycle. Cleared when sync server restarts —
    // we don't persist DOS state across reboots.
    val loginThrottle = LoginThrottle()

    routing {
        // Public: login
        authRoutes(db.userDao(), jwtConfig, loginThrottle)

        // Protected: everything else requires JWT
        authenticate("jwt-auth") {
            syncRoutes(db, deviceId)
            imageRoutes(imageStorageManager, db.productImageDao())
            crudRoutes(db)
        }
    }
}
