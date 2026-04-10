package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Simple key/value store for app-wide settings. Known keys:
 * `session_timeout`, `default_layaway_due_days`, `daily_export_password`,
 * `device_role`, `tailscale_ip`.
 */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val key: String,
    val value: String,
)
