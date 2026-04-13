package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.ProductImageEntity
import com.ykfj.inventory.domain.model.ProductImage

internal fun ProductImageEntity.toDomain(): ProductImage = ProductImage(
    id = image_id,
    productId = product_id,
    fileName = file_name,
    fileSizeBytes = file_size_bytes,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun ProductImage.toEntity(): ProductImageEntity = ProductImageEntity(
    image_id = id,
    product_id = productId,
    file_name = fileName,
    file_size_bytes = fileSizeBytes,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
