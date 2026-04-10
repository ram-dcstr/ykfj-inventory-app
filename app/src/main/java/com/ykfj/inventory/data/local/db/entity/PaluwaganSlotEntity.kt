package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A slot in a paluwagan group. One customer may own multiple slots within
 * the same group — each is a separate row with a distinct [position].
 *
 * [position] is 1-based collection order and may be swapped mid-cycle by
 * admin/manager via `SwapPaluwaganPositionsUseCase`.
 */
@Entity(
    tableName = "paluwagan_slots",
    indices = [
        Index(value = ["group_id"]),
        Index(value = ["customer_id"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class PaluwaganSlotEntity(
    @PrimaryKey val slot_id: String,
    val group_id: String,
    val customer_id: String,
    val position: Int,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
