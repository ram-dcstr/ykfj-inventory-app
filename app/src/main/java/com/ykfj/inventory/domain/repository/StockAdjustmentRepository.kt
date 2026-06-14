package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.StockAdjustment
import kotlinx.coroutines.flow.Flow

interface StockAdjustmentRepository {

    fun observeAll(): Flow<List<StockAdjustment>>

    fun observeForProduct(productId: String): Flow<List<StockAdjustment>>

    suspend fun getById(id: String): StockAdjustment?

    suspend fun insert(record: StockAdjustment)

    /** Soft delete — kept for a future "undo write-off" flow. */
    suspend fun softDelete(id: String)
}
