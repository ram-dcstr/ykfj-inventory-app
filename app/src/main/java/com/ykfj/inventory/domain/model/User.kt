package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.UserRole

/**
 * Domain representation of a user account.
 *
 * `password_hash` is intentionally absent — it never leaves the data layer.
 * Authentication use cases pass plaintext passwords to the repository, which
 * verifies them internally against the hash.
 */
data class User(
    val id: String,
    val username: String,
    val name: String,
    val role: UserRole,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
