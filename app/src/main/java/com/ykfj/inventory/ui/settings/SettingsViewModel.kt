package com.ykfj.inventory.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.BuildConfig
import com.ykfj.inventory.data.local.AppSettingKeys
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.entity.AppSettingsEntity
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.data.remote.sync.ConnectionResolver
import com.ykfj.inventory.data.remote.sync.NsdDiscovery
import com.ykfj.inventory.data.remote.sync.PhoneSyncForegroundService
import com.ykfj.inventory.data.remote.sync.ResolvedService
import com.ykfj.inventory.data.remote.sync.SyncForegroundService
import com.ykfj.inventory.data.remote.sync.SyncManager
import com.ykfj.inventory.data.remote.sync.SyncServerManager
import com.ykfj.inventory.data.repository.DeviceRoleManager
import com.ykfj.inventory.domain.sync.DeviceRole
import com.ykfj.inventory.ui.auth.IdleTimeout
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.ui.components.SnackbarController
import com.ykfj.inventory.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.NetworkInterface
import javax.inject.Inject

data class SettingsUiState(
    val deviceRole: DeviceRole = DeviceRole.TABLET,
    val isServerRunning: Boolean = false,
    val serverPort: Int = SyncServerManager.SERVER_PORT,
    val isSaving: Boolean = false,
    val tabletIp: String = "",
    val syncUsername: String = "",
    val syncStatus: SyncManager.SyncStatus = SyncManager.SyncStatus(),
    /** This device's own Tailscale tailnet address (only resolved on TABLET role). */
    val ownTailscaleIp: String? = null,
    /** This device's own LAN IPv4 (e.g. 192.168.1.50) — shown on the tablet as
     *  a manual-entry fallback when NSD auto-discovery fails on the phone. */
    val ownLanIp: String? = null,
    /** Service auto-discovered on the local WiFi via mDNS, when available. Surfaced
     *  on the phone so the user can confirm same-WiFi discovery is working. */
    val nsdDiscovered: ResolvedService? = null,
    // ── Session & App Info (Phase 6.6) ───────────────────────────────────────
    val idleTimeout: IdleTimeout = IdleTimeout.THIRTY_MIN,
    val dailyExportPassword: String = AppSettingKeys.DAILY_EXPORT_PASSWORD_FALLBACK,
    /** Phase 11: default opening cash float per day. Admin/Manager edit; staff view-only. */
    val defaultChangeFloat: Double = 0.0,
    /** Logged-in user's role — drives admin-only setting visibility. */
    val currentUserRole: UserRole? = null,
    val appVersion: String = BuildConfig.VERSION_NAME,
) {
    /**
     * "Working" means the sync section can stay collapsed in Settings. Anything
     * here returning false flips the section open so the user sees what to fix:
     *  - Tablet: server running
     *  - Phone : tablet IP configured AND no error AND at least one successful sync
     */
    val isSyncHealthy: Boolean
        get() = when (deviceRole) {
            DeviceRole.TABLET -> isServerRunning
            DeviceRole.PHONE -> tabletIp.isNotBlank() &&
                syncStatus.lastError == null &&
                (syncStatus.lastSyncTime > 0L || syncStatus.isSyncing)
        }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRoleManager: DeviceRoleManager,
    private val syncServerManager: SyncServerManager,
    private val syncManager: SyncManager,
    private val nsdDiscovery: NsdDiscovery,
    private val sessionManager: SessionManager,
    private val db: YkfjDatabase,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                deviceRoleManager.deviceRole,
                syncServerManager.isRunning,
            ) { role, running -> role to running }
                .collect { (role, running) ->
                    _uiState.update { it.copy(deviceRole = role, isServerRunning = running) }
                }
        }
        viewModelScope.launch {
            val ip = db.appSettingsDao().getValue(ConnectionResolver.KEY_TAILSCALE_IP) ?: ""
            val username = db.appSettingsDao().getValue(SyncManager.KEY_SYNC_USERNAME) ?: ""
            val exportPassword = db.appSettingsDao().getValue(AppSettingKeys.DAILY_EXPORT_PASSWORD)
                ?: AppSettingKeys.DAILY_EXPORT_PASSWORD_FALLBACK
            val changeFloat = db.appSettingsDao().getValue(AppSettingKeys.DEFAULT_CHANGE_FLOAT)
                ?.toDoubleOrNull() ?: 0.0
            _uiState.update {
                it.copy(
                    tabletIp = ip,
                    syncUsername = username,
                    dailyExportPassword = exportPassword,
                    defaultChangeFloat = changeFloat,
                )
            }
        }
        viewModelScope.launch {
            syncManager.status.collect { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }
        viewModelScope.launch {
            sessionManager.idleTimeoutFlow.collect { timeout ->
                _uiState.update { it.copy(idleTimeout = timeout) }
            }
        }
        viewModelScope.launch {
            sessionManager.currentUser.collect { user ->
                _uiState.update { it.copy(currentUserRole = user?.role) }
            }
        }
        viewModelScope.launch {
            val tsIp = withContext(Dispatchers.IO) { findOwnTailscaleIp() }
            val lanIp = withContext(Dispatchers.IO) { findOwnLanIp() }
            _uiState.update { it.copy(ownTailscaleIp = tsIp, ownLanIp = lanIp) }
        }
        // Start NSD discovery while Settings is open so the user can see live
        // whether the phone is finding the tablet on the local WiFi. Idempotent —
        // the PhoneSyncForegroundService also calls startDiscovery, this just
        // ensures the result is observed when the user is staring at the screen.
        nsdDiscovery.startDiscovery()
        viewModelScope.launch {
            nsdDiscovery.discoveredService.collect { resolved ->
                _uiState.update { it.copy(nsdDiscovered = resolved) }
            }
        }
    }

    /**
     * Walks the device's network interfaces looking for an address in the
     * Tailscale CGNAT range (100.64.0.0/10). Returns the first match, or null
     * when Tailscale isn't running. Surfaced on the tablet so the admin can
     * read the address off the screen instead of opening the Tailscale app.
     */
    private fun findOwnTailscaleIp(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }
            .getOrNull() ?: return null
        for (iface in interfaces.toList()) {
            if (!runCatching { iface.isUp }.getOrDefault(false)) continue
            for (addr in iface.inetAddresses.toList()) {
                if (addr.isLoopbackAddress) continue
                val host = addr.hostAddress ?: continue
                if (isTailscaleCgnat(host)) return host
            }
        }
        return null
    }

    private fun isTailscaleCgnat(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 100 && second in 64..127
    }

    /**
     * Walks the device's network interfaces for an IPv4 address in a private
     * LAN range (RFC 1918): 10/8, 172.16/12, 192.168/16. Returns the first
     * match. Used on the tablet so the user can read off the LAN IP and enter
     * it manually on the phone if NSD discovery isn't working on their router.
     */
    private fun findOwnLanIp(): String? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }
            .getOrNull() ?: return null
        for (iface in interfaces.toList()) {
            if (!runCatching { iface.isUp }.getOrDefault(false)) continue
            for (addr in iface.inetAddresses.toList()) {
                if (addr.isLoopbackAddress) continue
                val host = addr.hostAddress ?: continue
                // IPv4 only — skip v6 and the Tailscale CGNAT range.
                if (!host.matches(Regex("""\d+\.\d+\.\d+\.\d+"""))) continue
                if (isTailscaleCgnat(host)) continue
                if (isPrivateLan(host)) return host
            }
        }
        return null
    }

    private fun isPrivateLan(ip: String): Boolean {
        val parts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return when {
            parts[0] == 10 -> true
            parts[0] == 192 && parts[1] == 168 -> true
            parts[0] == 172 && parts[1] in 16..31 -> true
            else -> false
        }
    }

    /**
     * Switches the device role and immediately starts/stops the appropriate services:
     * - TABLET → starts [SyncForegroundService] (Ktor + NSD registration)
     * - PHONE  → stops [SyncForegroundService], starts NSD discovery, triggers initial sync
     */
    fun setDeviceRole(role: DeviceRole) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            deviceRoleManager.setRole(role)
            when (role) {
                DeviceRole.TABLET -> {
                    context.stopService(Intent(context, PhoneSyncForegroundService::class.java))
                    nsdDiscovery.stopDiscovery()
                    context.startForegroundService(
                        Intent(context, SyncForegroundService::class.java)
                    )
                }
                DeviceRole.PHONE -> {
                    context.stopService(Intent(context, SyncForegroundService::class.java))
                    context.startForegroundService(
                        Intent(context, PhoneSyncForegroundService::class.java)
                    )
                }
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    /** Saves tablet IP and sync account credentials to persistent settings. */
    fun savePhoneConfig(tabletIp: String, syncUsername: String, syncPassword: String) {
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            db.appSettingsDao().upsert(
                AppSettingsEntity(key = ConnectionResolver.KEY_TAILSCALE_IP, value = tabletIp.trim())
            )
            db.appSettingsDao().upsert(
                AppSettingsEntity(key = SyncManager.KEY_SYNC_USERNAME, value = syncUsername.trim())
            )
            if (syncPassword.isNotBlank()) {
                db.appSettingsDao().upsert(
                    AppSettingsEntity(key = SyncManager.KEY_SYNC_PASSWORD, value = syncPassword)
                )
            }
            _uiState.update { it.copy(
                isSaving = false,
                tabletIp = tabletIp.trim(),
                syncUsername = syncUsername.trim(),
            ) }
            snackbarController.showSuccess("Sync settings saved")
        }
    }

    /** Triggers an immediate sync cycle (phone role only). */
    fun syncNow() {
        viewModelScope.launch { syncManager.sync() }
    }

    // ── Phase 6.6 — Session & App Info ───────────────────────────────────────

    /** Updates [SessionManager] in-memory and persists the choice for next launch. */
    fun setIdleTimeout(timeout: IdleTimeout) {
        sessionManager.setIdleTimeout(timeout)
        viewModelScope.launch {
            db.appSettingsDao().upsert(
                AppSettingsEntity(key = AppSettingKeys.SESSION_TIMEOUT, value = timeout.name)
            )
            snackbarController.showSuccess("Idle timeout set to ${timeout.label}")
        }
    }

    /** Saves the daily-sales export PDF password. Admin-only — UI gating happens at the screen. */
    fun setDailyExportPassword(password: String) {
        val trimmed = password.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            db.appSettingsDao().upsert(
                AppSettingsEntity(key = AppSettingKeys.DAILY_EXPORT_PASSWORD, value = trimmed)
            )
            _uiState.update { it.copy(dailyExportPassword = trimmed) }
            snackbarController.showSuccess("Daily export password updated")
        }
    }

    /**
     * Phase 11: persists the default opening cash float. Used by DailyCashScreen
     * to auto-create the CHANGE_FLOAT row when the shop opens a new day with no
     * existing entry. Admin/Manager only — gated at the UI layer.
     */
    fun setDefaultChangeFloat(amount: Double) {
        if (amount < 0) return
        viewModelScope.launch {
            db.appSettingsDao().upsert(
                AppSettingsEntity(key = AppSettingKeys.DEFAULT_CHANGE_FLOAT, value = amount.toString())
            )
            _uiState.update { it.copy(defaultChangeFloat = amount) }
            snackbarController.showSuccess("Default change float set to ${CurrencyFormatter.format(amount)}")
        }
    }
}
