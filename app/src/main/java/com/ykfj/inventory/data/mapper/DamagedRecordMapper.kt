package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.DamagedRecordEntity
import com.ykfj.inventory.domain.model.DamagedRecord

internal fun DamagedRecordEntity.toDomain(): DamagedRecord = DamagedRecord(
    id = damaged_id,
    productId = product_id,
    recordedBy = recorded_by,
    reason = reason,
    dateRecorded = date_recorded,
    notes = notes,
    isArchived = is_archived,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun DamagedRecord.toEntity(): DamagedRecordEntity = DamagedRecordEntity(
    damaged_id = id,
    product_id = productId,
    recorded_by = recordedBy,
    reason = reason,
    date_recorded = dateRecorded,
    notes = notes,
    is_archived = isArchived,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
