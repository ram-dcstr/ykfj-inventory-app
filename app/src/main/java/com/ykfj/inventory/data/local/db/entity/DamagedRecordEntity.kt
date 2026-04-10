package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Damaged-item record. Always 1 unit per row — if 3 units of a product are
 * damaged, there are 3 separate rows.
 */
@Entity(
    tableName = "damaged_records",
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["recorded_by"]),
        Index(value = ["date_recorded"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_archived"]),
        Index(value = ["is_deleted"]),
    ],
)
data class DamagedRecordEntity(
    @PrimaryKey val damaged_id: String,
    val product_id: String,
    val recorded_by: String,
    val reason: String,
    val date_recorded: Long,
    val notes: String? = null,
    val is_archived: Boolean = false,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
