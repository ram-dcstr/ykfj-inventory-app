package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.StockAdjustmentDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.StockAdjustment
import com.ykfj.inventory.domain.repository.StockAdjustmentRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class StockAdjustmentRepositoryImpl @Inject constructor(
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val syncEnqueuer: SyncEnqueuer,
) : StockAdjustmentRepository {

    override fun observeAll(): Flow<List<StockAdjustment>> =
        stockAdjustmentDao.observeActive().map { it.map { e -> e.toDomain() } }

    override fun observeForProduct(productId: String): Flow<List<StockAdjustment>> =
        stockAdjustmentDao.observeForProduct(productId).map { it.map { e -> e.toDomain() } }

    override suspend fun getById(id: String): StockAdjustment? =
        stockAdjustmentDao.getById(id)?.toDomain()

    override suspend fun insert(record: StockAdjustment) {
        val entity = record.toEntity()
        stockAdjustmentDao.insert(entity)
        syncEnqueuer.enqueueStockAdjustment(entity, SyncAction.INSERT)
    }

    override suspend fun softDelete(id: String) {
        val existing = stockAdjustmentDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        stockAdjustmentDao.softDelete(id, now)
        syncEnqueuer.enqueueStockAdjustment(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }
}
