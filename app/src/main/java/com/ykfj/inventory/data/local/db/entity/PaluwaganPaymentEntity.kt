package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus

/**
 * Per-slot, per-round payment row. The `(group_id, slot_id, round_number)`
 * tuple is effectively unique — business logic rejects duplicates.
 */
@Entity(
    tableName = "paluwagan_payments",
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["slot_id"]),
        Index(value = ["round_number"]),
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class PaluwaganPaymentEntity(
    @PrimaryKey val payment_id: String,
    val group_id: String,
    val slot_id: String,
    val round_number: Int,
    val amount_paid: Double,
    /** Nullable while status == UNPAID. */
    val payment_date: Long? = null,
    val status: PaluwaganPaymentStatus = PaluwaganPaymentStatus.UNPAID,
    val notes: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
