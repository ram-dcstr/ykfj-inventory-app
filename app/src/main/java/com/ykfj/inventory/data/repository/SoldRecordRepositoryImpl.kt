package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.SoldRecordDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.SoldRecord
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SoldRecordRepositoryImpl @Inject constructor(
    private val soldRecordDao: SoldRecordDao,
    private val syncEnqueuer: SyncEnqueuer,
) : SoldRecordRepository {

    override fun observeByDateRange(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<SoldRecord>> =
        soldRecordDao.observeBetween(fromEpochMillis, toEpochMillis).map { it.map { e -> e.toDomain() } }

    override fun observeForProduct(productId: String): Flow<List<SoldRecord>> =
        soldRecordDao.observeForProduct(productId).map { it.map { e -> e.toDomain() } }

    override fun observeForCustomer(customerId: String): Flow<List<SoldRecord>> =
        soldRecordDao.observeByCustomer(customerId).map { it.map { e -> e.toDomain() } }

    override suspend fun getById(id: String): SoldRecord? =
        soldRecordDao.getById(id)?.toDomain()

    override suspend fun getMostRecentForProduct(productId: String): SoldRecord? =
        soldRecordDao.getMostRecentForProduct(productId)?.toDomain()

    override suspend fun countActiveForProduct(productId: String): Int =
        soldRecordDao.countActiveForProduct(productId)

    override suspend fun findByLayawayCompletion(layawayId: String): SoldRecord? =
        soldRecordDao.findByNotes("layaway_complete:$layawayId")?.toDomain()

    override suspend fun insert(record: SoldRecord) {
        val entity = record.toEntity()
        soldRecordDao.insert(entity)
        syncEnqueuer.enqueueSoldRecord(entity, SyncAction.INSERT)
    }

    override suspend fun update(record: SoldRecord) {
        val entity = record.toEntity()
        soldRecordDao.update(entity)
        syncEnqueuer.enqueueSoldRecord(entity, SyncAction.UPDATE)
    }

    override suspend fun softDelete(id: String) {
        val existing = soldRecordDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        soldRecordDao.softDelete(id, now)
        syncEnqueuer.enqueueSoldRecord(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }

    override suspend fun archive(id: String) {
        val existing = soldRecordDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        soldRecordDao.archive(id, now)
        syncEnqueuer.enqueueSoldRecord(existing.copy(is_archived = true, updated_at = now))
    }
}
