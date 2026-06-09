package com.ykfj.inventory.data.remote.sync

import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.entity.AppSettingsEntity
import javax.inject.Inject
import javax.inject.Singleton

/** Host + port of the tablet's Ktor server. */
data class ConnectionInfo(val host: String, val port: Int)

/**
 * Resolves the best available connection to the tablet's sync server.
 *
 * Priority:
 * 1. NSD-discovered host on same WiFi — zero-config, fastest
 * 2. Tailscale IP stored in [AppSettingsDao] (key: [KEY_TAILSCALE_IP]) — for remote/off-WiFi access
 * 3. Last-known-good host cached in [AppSettingsDao] — fallback when offline and NSD unavailable
 *
 * Returns null when no connection info is available at all.
 *
 * Call [resolve] from the phone-side sync flow before each HTTP request.
 * The Tailscale IP is configured by the user in Settings (Phase 6.6).
 */
@Singleton
class ConnectionResolver @Inject constructor(
    private val nsdDiscovery: NsdDiscovery,
    private val db: YkfjDatabase,
) {

    suspend fun resolve(): ConnectionInfo? {
        // 1. NSD — same WiFi, no config required
        val nsdService = nsdDiscovery.discoveredService.value
        if (nsdService != null) {
            persistLastKnown(nsdService.host, nsdService.port)
            return ConnectionInfo(nsdService.host, nsdService.port)
        }

        // 2. Tailscale — remote access, user-configured IP
        val tailscaleIp = db.appSettingsDao().getValue(KEY_TAILSCALE_IP)
        if (!tailscaleIp.isNullOrBlank()) {
            return ConnectionInfo(tailscaleIp, SyncServerManager.SERVER_PORT)
        }

        // 3. Last-known-good — offline cache so the phone still knows where the tablet was
        val lastHost = db.appSettingsDao().getValue(KEY_LAST_KNOWN_HOST)
        val lastPort = db.appSettingsDao().getValue(KEY_LAST_KNOWN_PORT)?.toIntOrNull()
        if (!lastHost.isNullOrBlank() && lastPort != null) {
            return ConnectionInfo(lastHost, lastPort)
        }

        return null
    }

    private suspend fun persistLastKnown(host: String, port: Int) {
        db.appSettingsDao().upsert(AppSettingsEntity(key = KEY_LAST_KNOWN_HOST, value = host))
        db.appSettingsDao().upsert(AppSettingsEntity(key = KEY_LAST_KNOWN_PORT, value = port.toString()))
    }

    companion object {
        /** Key for the Tailscale IP entered by the user in Settings. */
        const val KEY_TAILSCALE_IP = "tailscale_ip"
        private const val KEY_LAST_KNOWN_HOST = "last_known_host"
        private const val KEY_LAST_KNOWN_PORT = "last_known_port"
    }
}
