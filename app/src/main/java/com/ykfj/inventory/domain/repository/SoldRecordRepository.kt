package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.SoldRecord
import kotlinx.coroutines.flow.Flow

interface SoldRecordRepository {

    /** Active (non-archived) records by date range, newest first. */
    fun observeByDateRange(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<SoldRecord>>

    fun observeForProduct(productId: String): Flow<List<SoldRecord>>

    fun observeForCustomer(customerId: String): Flow<List<SoldRecord>>

    suspend fun getById(id: String): SoldRecord?

    /** Most recent non-deleted sold record for a product — used by revert. */
    suspend fun getMostRecentForProduct(productId: String): SoldRecord?

    /** Finds the auto-created sold record from a layaway completion (the layaway-revert flow needs to clean it up). */
    suspend fun findByLayawayCompletion(layawayId: String): SoldRecord?

    suspend fun insert(record: SoldRecord)

    suspend fun update(record: SoldRecord)

    /** Soft delete — used by the Admin/Manager revert flow. */
    suspend fun softDelete(id: String)

    /** Flips `is_archived = true`. Purge is a separate hard-delete path. */
    suspend fun archive(id: String)
}
