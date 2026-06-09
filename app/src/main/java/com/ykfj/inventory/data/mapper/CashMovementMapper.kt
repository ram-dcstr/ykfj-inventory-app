package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.CashMovementEntity
import com.ykfj.inventory.domain.model.CashMovement

fun CashMovementEntity.toDomain() = CashMovement(
    id = id,
    type = type,
    amount = amount,
    date = date,
    notes = notes,
    recordedBy = recorded_by,
    recordedAt = recorded_at,
    createdAt = created_at,
    updatedAt = updated_at,
    isDeleted = is_deleted,
)

fun CashMovement.toEntity() = CashMovementEntity(
    id = id,
    type = type,
    amount = amount,
    date = date,
    notes = notes,
    recorded_by = recordedBy,
    recorded_at = recordedAt,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = isDeleted,
)
