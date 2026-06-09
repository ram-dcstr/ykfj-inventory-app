package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.AppSettingsDao
import com.ykfj.inventory.data.local.db.entity.AppSettingsEntity
import com.ykfj.inventory.domain.sync.DeviceRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the device-role setting stored in [AppSettingsDao].
 *
 * Key: [KEY_DEVICE_ROLE] ("device_role").
 * Default when absent: [DeviceRole.TABLET] — the tablet is always set up first.
 *
 * [PendingSyncManagerImpl] reads the same key to decide whether to enqueue
 * offline changes (phone) or skip (tablet).
 */
@Singleton
class DeviceRoleManager @Inject constructor(
    private val appSettingsDao: AppSettingsDao,
) {
    /** Reactive stream of the current device role. Replays the latest value on collection. */
    val deviceRole: Flow<DeviceRole> = appSettingsDao
        .observeValue(KEY_DEVICE_ROLE)
        .map { raw -> raw?.let { runCatching { DeviceRole.valueOf(it) }.getOrNull() } ?: DeviceRole.TABLET }

    /** One-shot read — use [deviceRole] for reactive UI. */
    suspend fun getRole(): DeviceRole {
        val raw = appSettingsDao.getValue(KEY_DEVICE_ROLE)
        return raw?.let { runCatching { DeviceRole.valueOf(it) }.getOrNull() } ?: DeviceRole.TABLET
    }

    /** Persists the selected role. The change is reflected in [deviceRole] immediately. */
    suspend fun setRole(role: DeviceRole) {
        appSettingsDao.upsert(AppSettingsEntity(key = KEY_DEVICE_ROLE, value = role.name))
    }

    companion object {
        const val KEY_DEVICE_ROLE = "device_role"
    }
}
