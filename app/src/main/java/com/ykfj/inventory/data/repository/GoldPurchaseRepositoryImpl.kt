package com.ykfj.inventory.data.repository

import androidx.room.withTransaction
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.dao.GoldPurchaseItemDao
import com.ykfj.inventory.data.local.db.dao.GoldPurchaseRecordDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.GoldPurchaseItem
import com.ykfj.inventory.domain.model.GoldPurchaseRecord
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GoldPurchaseRepositoryImpl @Inject constructor(
    private val db: YkfjDatabase,
    private val recordDao: GoldPurchaseRecordDao,
    private val itemDao: GoldPurchaseItemDao,
    private val syncEnqueuer: SyncEnqueuer,
) : GoldPurchaseRepository {

    override fun observeAll(): Flow<List<GoldPurchaseRecord>> =
        recordDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeAllItems(): Flow<List<GoldPurchaseItem>> =
        itemDao.observeAll().map { it.map { e -> e.toDomain() } }

    override fun observeById(id: String): Flow<GoldPurchaseRecord?> =
        recordDao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): GoldPurchaseRecord? =
        recordDao.getById(id)?.toDomain()

    override fun observeItemsForRecord(recordId: String): Flow<List<GoldPurchaseItem>> =
        itemDao.observeForRecord(recordId).map { it.map { e -> e.toDomain() } }

    override suspend fun getItemsForRecord(recordId: String): List<GoldPurchaseItem> =
        itemDao.getForRecord(recordId).map { it.toDomain() }

    override suspend fun getItemCountForRecord(recordId: String): Int =
        itemDao.countForRecord(recordId)

    override suspend fun insert(record: GoldPurchaseRecord, items: List<GoldPurchaseItem>) {
        val recordEntity = record.toEntity()
        val itemEntities = items.map { it.toEntity() }
        db.withTransaction {
            recordDao.insert(recordEntity)
            itemDao.insertAll(itemEntities)
        }
        // Enqueue record first so the tablet has the FK target before items arrive.
        syncEnqueuer.enqueueGoldPurchaseRecord(recordEntity, SyncAction.INSERT)
        itemEntities.forEach { syncEnqueuer.enqueueGoldPurchaseItem(it, SyncAction.INSERT) }
    }

    override suspend fun softDelete(recordId: String, updatedAt: Long) {
        val existing = recordDao.getById(recordId) ?: return
        recordDao.softDelete(recordId, updatedAt)
        syncEnqueuer.enqueueGoldPurchaseRecord(
            existing.copy(is_deleted = true, updated_at = updatedAt),
            SyncAction.DELETE,
        )
    }

    override suspend fun softDeleteItems(recordId: String, updatedAt: Long) {
        val existingItems = itemDao.getForRecord(recordId)
        itemDao.softDeleteForRecord(recordId, updatedAt)
        existingItems.forEach {
            syncEnqueuer.enqueueGoldPurchaseItem(
                it.copy(is_deleted = true, updated_at = updatedAt),
                SyncAction.DELETE,
            )
        }
    }

    override suspend fun markItemSoldToSupplier(
        itemId: String,
        price: Double,
        soldAt: Long,
        updatedAt: Long,
    ) {
        itemDao.markSoldToSupplier(itemId, price, soldAt, updatedAt)
        itemDao.getById(itemId)?.let { syncEnqueuer.enqueueGoldPurchaseItem(it, SyncAction.UPDATE) }
    }

    override suspend fun unmarkItemSoldToSupplier(itemId: String, updatedAt: Long) {
        itemDao.unmarkSoldToSupplier(itemId, updatedAt)
        itemDao.getById(itemId)?.let { syncEnqueuer.enqueueGoldPurchaseItem(it, SyncAction.UPDATE) }
    }

    override fun observeSupplierProfit(start: Long, end: Long): Flow<Double> =
        itemDao.observeSupplierProfit(start, end)

    override fun observeSupplierRevenue(start: Long, end: Long): Flow<Double> =
        itemDao.observeSupplierRevenue(start, end)

    override fun observeSupplierSoldCount(start: Long, end: Long): Flow<Int> =
        itemDao.observeSupplierSoldCount(start, end)
}
