package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ykfj.inventory.data.local.db.enums.DiscountType

/**
 * A record of one sale transaction. Prices are **snapshotted** at sale time
 * so historical reports remain accurate even when metal rates later change.
 *
 * [sold_price] and [capital_price] are per-unit values; total revenue for
 * the row is `sold_price * quantity` (already reflects any discount applied).
 */
@Entity(
    tableName = "sold_records",
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["customer_id"]),
        Index(value = ["sold_by"]),
        Index(value = ["sold_date"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_archived"]),
        Index(value = ["is_deleted"]),
    ],
)
data class SoldRecordEntity(
    @PrimaryKey val sold_id: String,
    val product_id: String,
    val customer_id: String? = null,
    val sold_by: String,
    val quantity: Int,
    /** Per-unit final sale price (after discount). */
    val sold_price: Double,
    /** Per-unit capital snapshot. */
    val capital_price: Double,
    /** Discount per unit (0 if none). */
    val discount_amount: Double = 0.0,
    val discount_type: DiscountType = DiscountType.NONE,
    val sold_date: Long,
    val notes: String? = null,
    val is_archived: Boolean = false,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
