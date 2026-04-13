package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.CategoryEntity
import com.ykfj.inventory.domain.model.Category

internal fun CategoryEntity.toDomain(): Category = Category(
    id = category_id,
    name = name,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    category_id = id,
    name = name,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
