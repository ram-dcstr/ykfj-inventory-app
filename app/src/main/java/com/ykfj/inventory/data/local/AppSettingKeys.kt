package com.ykfj.inventory.data.local

/**
 * Centralized keys for the cross-cutting `app_settings` Phase 6.6 settings.
 * Sync- and role-specific keys still live next to the class that owns them
 * (e.g. [com.ykfj.inventory.data.repository.DeviceRoleManager.KEY_DEVICE_ROLE]).
 */
object AppSettingKeys {
    const val SESSION_TIMEOUT = "session_timeout"
    const val DAILY_EXPORT_PASSWORD = "daily_export_password"
    const val DEFAULT_CHANGE_FLOAT = "default_change_float"

    const val DAILY_EXPORT_PASSWORD_FALLBACK = "ykfj2024"
}
