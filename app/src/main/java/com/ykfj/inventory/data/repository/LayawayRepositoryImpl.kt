package com.ykfj.inventory.data.repository

import androidx.room.withTransaction
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.dao.LayawayRecordDao
import com.ykfj.inventory.data.local.db.dao.LayawayTransactionDao
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.model.LayawayTransaction
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LayawayRepositoryImpl @Inject constructor(
    private val db: YkfjDatabase,
    private val layawayRecordDao: LayawayRecordDao,
    private val layawayTransactionDao: LayawayTransactionDao,
    private val syncEnqueuer: SyncEnqueuer,
) : LayawayRepository {

    override fun observeActive(): Flow<List<LayawayRecord>> =
        layawayRecordDao.observeActive().map { it.map { e -> e.toDomain() } }

    override fun observeOverdueCount(now: Long): Flow<Int> =
        layawayRecordDao.observeOverdueCount(now)

    override fun observeCompleted(): Flow<List<LayawayRecord>> =
        layawayRecordDao.observeCompleted().map { it.map { e -> e.toDomain() } }

    override fun observeForCustomer(customerId: String): Flow<List<LayawayRecord>> =
        layawayRecordDao.observeByCustomer(customerId).map { it.map { e -> e.toDomain() } }

    override fun observeTransactions(layawayId: String): Flow<List<LayawayTransaction>> =
        layawayTransactionDao.observeForLayaway(layawayId).map { it.map { e -> e.toDomain() } }

    override suspend fun getById(id: String): LayawayRecord? =
        layawayRecordDao.getById(id)?.toDomain()

    override suspend fun getActiveForProduct(productId: String): LayawayRecord? =
        layawayRecordDao.getActiveForProduct(productId)?.toDomain()

    override suspend fun countActiveForProduct(productId: String): Int =
        layawayRecordDao.countActiveForProduct(productId)

    override suspend fun insert(record: LayawayRecord) {
        val entity = record.toEntity()
        layawayRecordDao.insert(entity)
        syncEnqueuer.enqueueLayawayRecord(entity, SyncAction.INSERT)
    }

    override suspend fun update(record: LayawayRecord) {
        val entity = record.toEntity()
        layawayRecordDao.update(entity)
        syncEnqueuer.enqueueLayawayRecord(entity, SyncAction.UPDATE)
    }

    override suspend fun addPayment(transaction: LayawayTransaction) {
        val txEntity = transaction.toEntity()
        val now = System.currentTimeMillis()
        // Atomic: insert the txn row AND update the layaway's total_paid together.
        // If either step fails, neither commits and total_paid stays consistent.
        val updatedRecord = db.withTransaction {
            layawayTransactionDao.insert(txEntity)
            val newTotal = layawayTransactionDao.sumForLayaway(transaction.layawayId)
            layawayRecordDao.updateTotalPaid(transaction.layawayId, newTotal, now)
            layawayRecordDao.getById(transaction.layawayId)
        }
        syncEnqueuer.enqueueLayawayTransaction(txEntity, SyncAction.INSERT)
        updatedRecord?.let { syncEnqueuer.enqueueLayawayRecord(it) }
    }

    override suspend fun deletePayment(transactionId: String) {
        val txn = layawayTransactionDao.getById(transactionId) ?: return
        val now = System.currentTimeMillis()
        // Atomic: soft-delete the txn AND recompute total_paid together.
        val updatedRecord = db.withTransaction {
            layawayTransactionDao.softDelete(transactionId, now)
            val newTotal = layawayTransactionDao.sumForLayaway(txn.layaway_id)
            layawayRecordDao.updateTotalPaid(txn.layaway_id, newTotal, now)
            layawayRecordDao.getById(txn.layaway_id)
        }
        syncEnqueuer.enqueueLayawayTransaction(
            txn.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
        updatedRecord?.let { syncEnqueuer.enqueueLayawayRecord(it) }
    }

    override suspend fun markCompleted(id: String, completionDate: Long) {
        val now = System.currentTimeMillis()
        layawayRecordDao.updateStatus(id, LayawayStatus.COMPLETED, completionDate, null, now)
        layawayRecordDao.getById(id)?.let { syncEnqueuer.enqueueLayawayRecord(it) }
    }

    override suspend fun revertCompletion(id: String) {
        val now = System.currentTimeMillis()
        layawayRecordDao.updateStatus(id, LayawayStatus.ACTIVE, null, null, now)
        layawayRecordDao.getById(id)?.let { syncEnqueuer.enqueueLayawayRecord(it) }
    }

    override suspend fun markCancelled(id: String, completionDate: Long) {
        val record = layawayRecordDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        layawayRecordDao.updateStatus(
            id, LayawayStatus.CANCELLED, completionDate,
            record.total_paid, now,
        )
        layawayRecordDao.getById(id)?.let { syncEnqueuer.enqueueLayawayRecord(it) }
    }

    override suspend fun softDelete(id: String) {
        val existing = layawayRecordDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        layawayRecordDao.softDelete(id, now)
        syncEnqueuer.enqueueLayawayRecord(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }

    override suspend fun archive(id: String) {
        val existing = layawayRecordDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        layawayRecordDao.archive(id, now)
        syncEnqueuer.enqueueLayawayRecord(existing.copy(is_archived = true, updated_at = now))
    }
}
