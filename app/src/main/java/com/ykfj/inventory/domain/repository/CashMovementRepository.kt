package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.data.local.db.enums.CashMovementType
import com.ykfj.inventory.domain.model.CashMovement
import kotlinx.coroutines.flow.Flow

/**
 * Phase 11 cash drawer movements: CHANGE_FLOAT (opening cash), PURCHASE_FLOAT
 * (cash pulled to buy stock), EXPENSE, ADJUSTMENT.
 *
 * The DailyCashScreen sums these alongside same-day sales and layaway payments
 * to compute the running cash balance. Each day's movements are independent.
 */
interface CashMovementRepository {

    /** All non-deleted movements on a calendar day, ordered by recordedAt asc. */
    fun observeForDay(dayStartMillis: Long): Flow<List<CashMovement>>

    /**
     * The single movement of [type] on the given calendar day, if any.
     * CHANGE_FLOAT and PURCHASE_FLOAT are single-row-per-day by convention
     * — editing them updates the existing row rather than inserting a new one.
     */
    suspend fun getForTypeAndDay(type: CashMovementType, dayStartMillis: Long): CashMovement?

    /** Insert-or-update by primary key. Used for both new entries and edits. */
    suspend fun upsert(movement: CashMovement)

    /** Soft-deletes a movement (e.g. when admin removes a recorded expense). */
    suspend fun softDelete(id: String)
}
