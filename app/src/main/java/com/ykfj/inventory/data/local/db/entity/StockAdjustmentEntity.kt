package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A manual stock write-off — units removed from inventory for a reason other than
 * a sale, layaway, or damage (lost, stolen, miscount correction, returned to
 * supplier, etc.). One row per adjustment; [quantity] is the number of units removed.
 *
 * This is the auditable alternative to deleting a whole product just to fix a count:
 * it reduces `products.quantity` by [quantity] and keeps a permanent record of what
 * left and why.
 */
@Entity(
    tableName = "stock_adjustments",
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["recorded_by"]),
        Index(value = ["date_recorded"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_archived"]),
        Index(value = ["is_deleted"]),
    ],
)
data class StockAdjustmentEntity(
    @PrimaryKey val adjustment_id: String,
    val product_id: String,
    /** Number of units removed (>= 1). */
    val quantity: Int,
    /** Stored as StockAdjustmentReason.name. */
    val reason: String,
    val notes: String? = null,
    val recorded_by: String,
    val date_recorded: Long,
    val is_archived: Boolean = false,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
