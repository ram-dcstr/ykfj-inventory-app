package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.LayawayTransactionEntity
import com.ykfj.inventory.domain.model.LayawayTransaction

internal fun LayawayTransactionEntity.toDomain(): LayawayTransaction = LayawayTransaction(
    id = transaction_id,
    layawayId = layaway_id,
    amountPaid = amount_paid,
    paymentDate = payment_date,
    notes = notes,
    paymentMethod = payment_method,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun LayawayTransaction.toEntity(): LayawayTransactionEntity = LayawayTransactionEntity(
    transaction_id = id,
    layaway_id = layawayId,
    amount_paid = amountPaid,
    payment_date = paymentDate,
    notes = notes,
    payment_method = paymentMethod,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
