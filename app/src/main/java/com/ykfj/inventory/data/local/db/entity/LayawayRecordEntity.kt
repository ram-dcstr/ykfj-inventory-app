package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ykfj.inventory.data.local.db.enums.LayawayStatus

/**
 * A layaway reservation for a product.
 *
 * Only one ACTIVE layaway per product is allowed (enforced in business logic,
 * not via DB constraint, so sync can reconcile order-of-arrival).
 *
 * Total owed = [unit_price] × [quantity]. Payments accumulate into
 * [total_paid] via [LayawayTransactionEntity] rows.
 *
 * On CANCELLED: [forfeited_amount] is set to [total_paid] — no refunds,
 * forfeited payments become shop profit.
 */
@Entity(
    tableName = "layaway_records",
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["customer_id"]),
        Index(value = ["created_by"]),
        Index(value = ["status"]),
        Index(value = ["due_date"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_archived"]),
        Index(value = ["is_deleted"]),
    ],
)
data class LayawayRecordEntity(
    @PrimaryKey val layaway_id: String,
    val product_id: String,
    val customer_id: String,
    val created_by: String,
    val quantity: Int,
    val unit_price: Double,
    val total_paid: Double = 0.0,
    val due_date: Long? = null,
    val status: LayawayStatus = LayawayStatus.ACTIVE,
    /** Set when status moves to COMPLETED or CANCELLED. */
    val completion_date: Long? = null,
    /** Set to total_paid on CANCELLED. Null otherwise. */
    val forfeited_amount: Double? = null,
    val is_archived: Boolean = false,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
