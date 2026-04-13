package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.ProductEntity
import com.ykfj.inventory.domain.model.Product

internal fun ProductEntity.toDomain(): Product = Product(
    id = product_id,
    name = name,
    categoryId = category_id,
    metalRateId = metal_rate_id,
    supplierId = supplier_id,
    dateAcquired = date_acquired,
    pricingType = pricing_type,
    capitalPrice = capital_price,
    sellingPrice = selling_price,
    weightGrams = weight_grams,
    size = size,
    quantity = quantity,
    notes = notes,
    status = status,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun Product.toEntity(): ProductEntity = ProductEntity(
    product_id = id,
    name = name,
    category_id = categoryId,
    metal_rate_id = metalRateId,
    supplier_id = supplierId,
    date_acquired = dateAcquired,
    pricing_type = pricingType,
    capital_price = capitalPrice,
    selling_price = sellingPrice,
    weight_grams = weightGrams,
    size = size,
    quantity = quantity,
    notes = notes,
    status = status,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
