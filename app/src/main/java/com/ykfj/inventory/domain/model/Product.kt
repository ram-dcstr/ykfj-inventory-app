package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.data.local.db.enums.ProductStatus

/**
 * Domain representation of a product in inventory.
 *
 * Pricing rules (see docs/business/Pricing-and-Discounts.md):
 * - WEIGHTED: [sellingPrice] is always `null`. The live price is computed at
 *   display time as `weightGrams × metalRate.pricePerGram`.
 * - FIXED: [sellingPrice] is set; [weightGrams] and [metalRateId] are `null`.
 *
 * Mixed quantity/status: [status] stays AVAILABLE while at least one unit is
 * free, only flipping to SOLD when every unit is accounted for across
 * sold/damaged/layaway records.
 */
data class Product(
    val id: String,
    val name: String,
    val categoryId: String,
    val metalRateId: String?,
    val supplierId: String?,
    val dateAcquired: Long,
    val pricingType: PricingType,
    val capitalPrice: Double,
    val sellingPrice: Double?,
    val weightGrams: Double?,
    val size: String?,
    val quantity: Int,
    val notes: String?,
    val status: ProductStatus,
    val createdAt: Long,
    val updatedAt: Long,
)
