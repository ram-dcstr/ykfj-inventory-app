package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Header record for a gold purchase transaction. Each record may have one or
 * more [GoldPurchaseItemEntity] lines. [linked_sold_record_id] is null for
 * straight purchases; Phase 10 sets it when the purchase is part of a trade-in.
 */
@Entity(
    tableName = "gold_purchase_records",
    indices = [
        Index(value = ["customer_id"]),
        Index(value = ["paid_at"]),
        Index(value = ["recorded_by"]),
        Index(value = ["linked_sold_record_id"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class GoldPurchaseRecordEntity(
    @PrimaryKey val id: String,
    val customer_id: String? = null,
    /** Sum of [GoldPurchaseItemEntity.final_value] for all items on this record. */
    val total_paid: Double,
    val paid_at: Long,
    val notes: String? = null,
    val recorded_by: String,
    /** Phase 10: links this purchase to the sold record it was traded against. */
    val linked_sold_record_id: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
