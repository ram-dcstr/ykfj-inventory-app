package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.StockAdjustmentEntity
import com.ykfj.inventory.data.local.db.enums.StockAdjustmentReason
import com.ykfj.inventory.domain.model.StockAdjustment

internal fun StockAdjustmentEntity.toDomain(): StockAdjustment = StockAdjustment(
    id = adjustment_id,
    productId = product_id,
    quantity = quantity,
    reason = runCatching { StockAdjustmentReason.valueOf(reason) }
        .getOrDefault(StockAdjustmentReason.OTHER),
    notes = notes,
    recordedBy = recorded_by,
    dateRecorded = date_recorded,
    isArchived = is_archived,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun StockAdjustment.toEntity(): StockAdjustmentEntity = StockAdjustmentEntity(
    adjustment_id = id,
    product_id = productId,
    quantity = quantity,
    reason = reason.name,
    notes = notes,
    recorded_by = recordedBy,
    date_recorded = dateRecorded,
    is_archived = isArchived,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
