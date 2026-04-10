package com.ykfj.inventory.data.local.db.entity

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
)
