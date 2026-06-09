package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.DamagedRecordDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.DamagedRecord
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DamagedRecordRepositoryImpl @Inject constructor(
    private val damagedRecordDao: DamagedRecordDao,
    private val syncEnqueuer: SyncEnqueuer,
) : DamagedRecordRepository {

    override fun observeAll(): Flow<List<DamagedRecord>> =
        damagedRecordDao.observeActive().map { it.map { e -> e.toDomain() } }

    override fun observeMelted(): Flow<List<DamagedRecord>> =
        damagedRecordDao.observeMelted().map { it.map { e -> e.toDomain() } }

    override fun observeForProduct(productId: String): Flow<List<DamagedRecord>> =
        damagedRecordDao.observeForProduct(productId).map { it.map { e -> e.toDomain() } }

    override suspend fun getById(id: String): DamagedRecord? =
        damagedRecordDao.getById(id)?.toDomain()

    override suspend fun getMostRecentForProduct(productId: String): DamagedRecord? =
        damagedRecordDao.getMostRecentForProduct(productId)?.toDomain()

    override suspend fun insert(record: DamagedRecord) {
        val entity = record.toEntity()
        damagedRecordDao.insert(entity)
        syncEnqueuer.enqueueDamagedRecord(entity, SyncAction.INSERT)
    }

    override suspend fun update(record: DamagedRecord) {
        val entity = record.toEntity()
        damagedRecordDao.update(entity)
        syncEnqueuer.enqueueDamagedRecord(entity, SyncAction.UPDATE)
    }

    override suspend fun softDelete(id: String) {
        val existing = damagedRecordDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        damagedRecordDao.softDelete(id, now)
        syncEnqueuer.enqueueDamagedRecord(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }

    override suspend fun restore(id: String) {
        val existing = damagedRecordDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        damagedRecordDao.restore(id, now)
        syncEnqueuer.enqueueDamagedRecord(
            existing.copy(is_deleted = false, updated_at = now),
            SyncAction.UPDATE,
        )
    }

    override suspend fun archive(id: String) {
        val existing = damagedRecordDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        damagedRecordDao.archive(id, now)
        syncEnqueuer.enqueueDamagedRecord(existing.copy(is_archived = true, updated_at = now))
    }
}
