package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.PaluwaganSlotEntity
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.PaluwaganSlot

internal fun PaluwaganSlotEntity.toDomain(): PaluwaganSlot = PaluwaganSlot(
    id = slot_id,
    groupId = group_id,
    customerId = customer_id,
    originalCustomerId = original_customer_id,
    position = position,
    potCollectedAt = pot_collected_at,
    potPayoutChannel = pot_payout_channel?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() },
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun PaluwaganSlot.toEntity(): PaluwaganSlotEntity = PaluwaganSlotEntity(
    slot_id = id,
    group_id = groupId,
    customer_id = customerId,
    original_customer_id = originalCustomerId,
    position = position,
    pot_collected_at = potCollectedAt,
    pot_payout_channel = potPayoutChannel?.name,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
