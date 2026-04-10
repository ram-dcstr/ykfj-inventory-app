package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One payment line on a layaway. Split payments across multiple layaways
 * create one [LayawayTransactionEntity] per layaway with the allocated amount.
 *
 * Admin may delete individual rows — [LayawayRecordEntity.total_paid] must
 * be recalculated by the use case after any delete.
 */
@Entity(
    tableName = "layaway_transactions",
    indices = [
        Index(value = ["layaway_id"]),
        Index(value = ["payment_date"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class LayawayTransactionEntity(
    @PrimaryKey val transaction_id: String,
    val layaway_id: String,
    val amount_paid: Double,
    val payment_date: Long,
    val notes: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
