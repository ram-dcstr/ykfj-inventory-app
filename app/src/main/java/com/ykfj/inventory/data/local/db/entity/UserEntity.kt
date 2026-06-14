package com.ykfj.inventory.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ykfj.inventory.data.local.db.enums.UserRole

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class UserEntity(
    @PrimaryKey val user_id: String,
    val username: String,
    val password_hash: String,
    val name: String,
    val role: UserRole,
    val is_active: Boolean = true,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
    /**
     * When `true`, the user is forced to set a new password on next login before
     * reaching the app. Seeded `true` on the release default admin so the shipped
     * `admin` account can't keep its well-known bootstrap password. Cleared once
     * the user picks their own password. `defaultValue` lets the v11→v12
     * auto-migration add the column to existing installs.
     */
    @ColumnInfo(defaultValue = "0") val must_change_password: Boolean = false,
)
