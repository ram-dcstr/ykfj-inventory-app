package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.data.local.db.enums.ProductStatus

/**
 * Products table.
 *
 * Pricing rules (see docs/business/Pricing-and-Discounts.md):
 * - WEIGHTED items: [selling_price] is always NULL. The live price is
 *   computed at display time as `weight_grams × metal_rate.price_per_gram`.
 * - FIXED items: [selling_price] is stored; [weight_grams] and [metal_rate_id]
 *   are NULL.
 *
 * Mixed quantity/status model: [status] stays AVAILABLE while at least one
 * unit is free and only flips to SOLD when every unit is accounted for.
 */
@Entity(
    tableName = "products",
    indices = [
        Index(value = ["status"]),
        Index(value = ["category_id"]),
        Index(value = ["metal_rate_id"]),
        Index(value = ["supplier_id"]),
        Index(value = ["updated_at"]),
        Index(value = ["date_acquired"]),
        Index(value = ["is_deleted"]),
    ],
)
data class ProductEntity(
    /** Auto-generated: `{NAME}-{METALRATE}-{CATEGORY}-{6-digit-seq}`. */
    @PrimaryKey val product_id: String,
    val name: String,
    val category_id: String,
    val metal_rate_id: String? = null,
    val supplier_id: String? = null,
    /** Epoch millis — when the product arrived. Required. */
    val date_acquired: Long,
    val pricing_type: PricingType,
    val capital_price: Double,
    /** Nullable — FIXED items only. WEIGHTED items compute price dynamically. */
    val selling_price: Double? = null,
    /** Nullable — WEIGHTED items only. */
    val weight_grams: Double? = null,
    val size: String? = null,
    val quantity: Int,
    val notes: String? = null,
    val status: ProductStatus = ProductStatus.AVAILABLE,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
