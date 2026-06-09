package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.PaluwaganPaymentEntity
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.PaluwaganPayment

internal fun PaluwaganPaymentEntity.toDomain(): PaluwaganPayment = PaluwaganPayment(
    id = payment_id,
    groupId = group_id,
    slotId = slot_id,
    roundNumber = round_number,
    amountPaid = amount_paid,
    paymentDate = payment_date,
    status = status,
    notes = notes,
    paymentMethod = payment_channel?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() },
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun PaluwaganPayment.toEntity(): PaluwaganPaymentEntity = PaluwaganPaymentEntity(
    payment_id = id,
    group_id = groupId,
    slot_id = slotId,
    round_number = roundNumber,
    amount_paid = amountPaid,
    payment_date = paymentDate,
    status = status,
    notes = notes,
    payment_channel = paymentMethod?.name,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
