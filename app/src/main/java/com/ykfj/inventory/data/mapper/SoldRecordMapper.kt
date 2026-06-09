package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.SoldRecordEntity
import com.ykfj.inventory.domain.model.SoldRecord

internal fun SoldRecordEntity.toDomain(): SoldRecord = SoldRecord(
    id = sold_id,
    productId = product_id,
    customerId = customer_id,
    soldBy = sold_by,
    quantity = quantity,
    soldPrice = sold_price,
    capitalPrice = capital_price,
    discountAmount = discount_amount,
    discountType = discount_type,
    soldDate = sold_date,
    notes = notes,
    paymentMethod = payment_method,
    isArchived = is_archived,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun SoldRecord.toEntity(): SoldRecordEntity = SoldRecordEntity(
    sold_id = id,
    product_id = productId,
    customer_id = customerId,
    sold_by = soldBy,
    quantity = quantity,
    sold_price = soldPrice,
    capital_price = capitalPrice,
    discount_amount = discountAmount,
    discount_type = discountType,
    sold_date = soldDate,
    notes = notes,
    payment_method = paymentMethod,
    is_archived = isArchived,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
