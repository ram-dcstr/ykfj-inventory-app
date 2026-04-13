package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.MetalRateEntity
import com.ykfj.inventory.domain.model.MetalRate

internal fun MetalRateEntity.toDomain(): MetalRate = MetalRate(
    id = rate_id,
    name = name,
    pricePerGram = price_per_gram,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun MetalRate.toEntity(): MetalRateEntity = MetalRateEntity(
    rate_id = id,
    name = name,
    price_per_gram = pricePerGram,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
