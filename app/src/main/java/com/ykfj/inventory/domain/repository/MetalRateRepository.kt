package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.MetalRate
import kotlinx.coroutines.flow.Flow

interface MetalRateRepository {

    fun observeAll(): Flow<List<MetalRate>>

    suspend fun getById(id: String): MetalRate?

    suspend fun upsert(metalRate: MetalRate)

    /**
     * Soft delete. Callers must first ensure no active products reference
     * this rate — see the deletion guard in `Inventory-Rules.md`.
     */
    suspend fun delete(id: String)

    /** Number of non-deleted products referencing [rateId] — used by delete guard. */
    suspend fun countActiveProducts(rateId: String): Int
}
