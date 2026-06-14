package com.ykfj.inventory.data.remote.sync

import android.util.Log
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
        if (nsdService != null && isSafeSyncHost(nsdService.host)) {
            persistLastKnown(nsdService.host, nsdService.port)
            return ConnectionInfo(nsdService.host, nsdService.port)
        }

        // 2. Tailscale — remote access, user-configured IP
        val tailscaleIp = db.appSettingsDao().getValue(KEY_TAILSCALE_IP)
        if (!tailscaleIp.isNullOrBlank() && isSafeSyncHost(tailscaleIp)) {
            return ConnectionInfo(tailscaleIp, SyncServerManager.SERVER_PORT)
        }

        // 3. Last-known-good — offline cache so the phone still knows where the tablet was
        val lastHost = db.appSettingsDao().getValue(KEY_LAST_KNOWN_HOST)
        val lastPort = db.appSettingsDao().getValue(KEY_LAST_KNOWN_PORT)?.toIntOrNull()
        if (!lastHost.isNullOrBlank() && lastPort != null && isSafeSyncHost(lastHost)) {
            return ConnectionInfo(lastHost, lastPort)
        }

        return null
    }

    /**
     * Defence-in-depth: only allow connecting to addresses that should plausibly
     * host our Ktor server.
     *
     *  - RFC 1918 private LAN (10/8, 172.16/12, 192.168/16)
     *  - Tailscale CGNAT (100.64/10)
     *  - IPv4 link-local (169.254/16) for no-DHCP fallbacks
     *  - localhost / emulator loopback
     *  - Anything that isn't a v4 dotted-quad (mDNS `.local`, IPv6, etc.) — we
     *    can't pattern-check those reliably, so we let them through with a log
     *    and trust the NSD / user-configured layer above us.
     *
     * Rejecting a public IPv4 here makes it impossible to silently sync to a
     * host on the open internet even if NSD is spoofed, the Tailscale field is
     * corrupted, or the cached last-known-good IP gets clobbered.
     */
    private fun isSafeSyncHost(host: String): Boolean {
        val v4 = V4_PATTERN.matchEntire(host) ?: run {
            // Not a v4 literal — let mDNS, IPv6 etc. through unchecked.
            return true
        }
        val (a, b) = v4.groupValues[1].toInt() to v4.groupValues[2].toInt()
        val allowed = when {
            a == 10 -> true                                // 10.0.0.0/8
            a == 172 && b in 16..31 -> true                // 172.16.0.0/12
            a == 192 && b == 168 -> true                   // 192.168.0.0/16
            a == 100 && b in 64..127 -> true               // Tailscale 100.64.0.0/10
            a == 169 && b == 254 -> true                   // IPv4 link-local
            a == 127 -> true                               // loopback
            else -> false
        }
        if (!allowed) {
            Log.w(
                "ConnectionResolver",
                "Refusing sync host $host — not in RFC1918 / Tailscale / loopback ranges",
            )
        }
        return allowed
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
        private val V4_PATTERN = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")
    }
}
