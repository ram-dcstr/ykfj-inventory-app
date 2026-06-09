package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ykfj.inventory.data.local.db.enums.CashMovementType

/**
 * One cash-flow event in the store's daily cash tally.
 *
 * [amount] is signed: positive = cash coming in (e.g. CHANGE_FLOAT added),
 * negative = cash going out (e.g. EXPENSE paid). This lets the Daily Cash
 * screen sum all rows for a [date] to produce a running balance.
 *
 * [date] is stored as start-of-day millis (midnight local time) so queries
 * can filter by calendar day without timezone math at read time.
 */
@Entity(
    tableName = "cash_movements",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["recorded_by"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class CashMovementEntity(
    @PrimaryKey val id: String,
    val type: CashMovementType,
    val amount: Double,
    val date: Long,
    val notes: String? = null,
    val recorded_by: String,
    val recorded_at: Long,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
