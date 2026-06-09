package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.PaluwaganGroupEntity
import com.ykfj.inventory.domain.model.PaluwaganGroup

internal fun PaluwaganGroupEntity.toDomain(): PaluwaganGroup = PaluwaganGroup(
    id = group_id,
    name = name,
    contributionAmount = contribution_amount,
    frequencyDays = frequency_days,
    totalSlots = total_slots,
    currentRound = current_round,
    status = status,
    startDate = start_date,
    notes = notes,
    isArchived = is_archived,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun PaluwaganGroup.toEntity(): PaluwaganGroupEntity = PaluwaganGroupEntity(
    group_id = id,
    name = name,
    contribution_amount = contributionAmount,
    frequency_days = frequencyDays,
    total_slots = totalSlots,
    current_round = currentRound,
    status = status,
    start_date = startDate,
    notes = notes,
    is_archived = isArchived,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
