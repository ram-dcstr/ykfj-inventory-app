package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.DamagedRecord
import kotlinx.coroutines.flow.Flow

interface DamagedRecordRepository {

    fun observeAll(): Flow<List<DamagedRecord>>

    /** Damaged records that were melted: both the record and the product are soft-deleted. */
    fun observeMelted(): Flow<List<DamagedRecord>>

    fun observeForProduct(productId: String): Flow<List<DamagedRecord>>

    suspend fun getById(id: String): DamagedRecord?

    /** Most recent non-deleted damaged record for a product — used by revert. */
    suspend fun getMostRecentForProduct(productId: String): DamagedRecord?

    suspend fun insert(record: DamagedRecord)

    suspend fun update(record: DamagedRecord)

    /** Soft delete — used by the Admin/Manager revert flow. */
    suspend fun softDelete(id: String)

    /** Inverse of [softDelete] — flips `is_deleted` back to 0. Used by the melt-revert flow. */
    suspend fun restore(id: String)

    suspend fun archive(id: String)
}
