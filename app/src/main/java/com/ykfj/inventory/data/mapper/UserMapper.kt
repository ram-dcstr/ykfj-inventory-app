package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.UserEntity
import com.ykfj.inventory.domain.model.User

/**
 * Maps [UserEntity] ↔ [User]. The `password_hash` column intentionally
 * does not map to the domain model — it never leaves the data layer.
 * To create or update a hash, callers use [toEntity] with an explicit
 * `passwordHash` argument.
 */
internal fun UserEntity.toDomain(): User = User(
    id = user_id,
    username = username,
    name = name,
    role = role,
    isActive = is_active,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun User.toEntity(passwordHash: String): UserEntity = UserEntity(
    user_id = id,
    username = username,
    password_hash = passwordHash,
    name = name,
    role = role,
    is_active = isActive,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
