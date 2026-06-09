package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.MetalRateDao
import com.ykfj.inventory.data.local.db.dao.ProductDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.repository.MetalRateRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MetalRateRepositoryImpl @Inject constructor(
    private val metalRateDao: MetalRateDao,
    private val productDao: ProductDao,
    private val syncEnqueuer: SyncEnqueuer,
) : MetalRateRepository {

    override fun observeAll(): Flow<List<MetalRate>> =
        metalRateDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): MetalRate? =
        metalRateDao.getById(id)?.toDomain()

    override suspend fun upsert(metalRate: MetalRate) {
        val entity = metalRate.toEntity()
        val existing = metalRateDao.getById(metalRate.id)
        if (existing == null) {
            metalRateDao.insert(entity)
            syncEnqueuer.enqueueMetalRate(entity, SyncAction.INSERT)
        } else {
            metalRateDao.update(entity)
            syncEnqueuer.enqueueMetalRate(entity, SyncAction.UPDATE)
        }
    }

    override suspend fun delete(id: String) {
        val existing = metalRateDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        metalRateDao.softDelete(id, now)
        syncEnqueuer.enqueueMetalRate(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }

    override suspend fun countActiveProducts(rateId: String): Int =
        productDao.countByMetalRate(rateId)
}
