package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.DamagedRecord
import kotlinx.coroutines.flow.Flow

interface DamagedRecordRepository {

    fun observeAll(): Flow<List<DamagedRecord>>

    fun observeForProduct(productId: String): Flow<List<DamagedRecord>>

    suspend fun getById(id: String): DamagedRecord?

    suspend fun insert(record: DamagedRecord)

    /** Soft delete — used by the Admin/Manager revert flow. */
    suspend fun softDelete(id: String)

    suspend fun archive(id: String)
}
