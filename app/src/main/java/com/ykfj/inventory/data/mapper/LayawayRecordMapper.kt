package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.LayawayRecordEntity
import com.ykfj.inventory.domain.model.LayawayRecord

internal fun LayawayRecordEntity.toDomain(): LayawayRecord = LayawayRecord(
    id = layaway_id,
    productId = product_id,
    customerId = customer_id,
    createdBy = created_by,
    quantity = quantity,
    unitPrice = unit_price,
    totalPaid = total_paid,
    dueDate = due_date,
    status = status,
    completionDate = completion_date,
    forfeitedAmount = forfeited_amount,
    isArchived = is_archived,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun LayawayRecord.toEntity(): LayawayRecordEntity = LayawayRecordEntity(
    layaway_id = id,
    product_id = productId,
    customer_id = customerId,
    created_by = createdBy,
    quantity = quantity,
    unit_price = unitPrice,
    total_paid = totalPaid,
    due_date = dueDate,
    status = status,
    completion_date = completionDate,
    forfeited_amount = forfeitedAmount,
    is_archived = isArchived,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
