package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.PaluwaganSlotEntity
import com.ykfj.inventory.domain.model.PaluwaganSlot

internal fun PaluwaganSlotEntity.toDomain(): PaluwaganSlot = PaluwaganSlot(
    id = slot_id,
    groupId = group_id,
    customerId = customer_id,
    position = position,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun PaluwaganSlot.toEntity(): PaluwaganSlotEntity = PaluwaganSlotEntity(
    slot_id = id,
    group_id = groupId,
    customer_id = customerId,
    position = position,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
