package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.CashMovementType

/**
 * One cash-flow event in the store's daily cash tally.
 *
 * [amount] is signed: positive = cash coming in (CHANGE_FLOAT, PURCHASE_FLOAT,
 * positive ADJUSTMENT), negative = cash going out (EXPENSE, negative ADJUSTMENT).
 *
 * [date] is the start-of-day millis (midnight local time) the movement is
 * attributed to — the DailyCashScreen sums movements for a given calendar day.
 * [recordedAt] is the actual wall-clock when the user logged it.
 */
data class CashMovement(
    val id: String,
    val type: CashMovementType,
    val amount: Double,
    val date: Long,
    val notes: String? = null,
    val recordedBy: String,
    val recordedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)
