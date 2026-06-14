package com.ykfj.inventory.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.ykfj.inventory.data.local.db.dao.ProductDao
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject



class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val syncEnqueuer: SyncEnqueuer,
) : ProductRepository {

    override fun observeProductsPaged(showSold: Boolean): Flow<PagingData<Product>> =
        Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            if (showSold) productDao.pagingAll() else productDao.pagingAvailable()
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override fun searchProductsPaged(query: String): Flow<PagingData<Product>> {
        val sanitized = query.trim().replace(Regex("[^\\w\\s]"), "")
        if (sanitized.isBlank()) return flowOf(PagingData.empty())
        val ftsQuery = sanitized.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
        return Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
            productDao.searchPaging(ftsQuery)
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }
    }

    override suspend fun getById(id: String): Product? =
        productDao.getById(id)?.toDomain()

    override suspend fun getByIdAnyState(id: String): Product? =
        productDao.getByIdAnyState(id)?.toDomain()

    override fun observeById(id: String): Flow<Product?> =
        productDao.observeById(id).map { it?.toDomain() }

    override suspend fun upsert(product: Product) {
        val existing = productDao.getById(product.id)
        val entity = product.toEntity()
        if (existing == null) {
            productDao.insert(entity)
            syncEnqueuer.enqueueProduct(entity, SyncAction.INSERT)
        } else {
            productDao.update(entity)
            syncEnqueuer.enqueueProduct(entity, SyncAction.UPDATE)
        }
    }

    override suspend fun tryAddNew(product: Product): Boolean {
        val entity = product.toEntity()
        return try {
            productDao.insert(entity) // ABORTs on UNIQUE collision → throws
            syncEnqueuer.enqueueProduct(entity, SyncAction.INSERT)
            true
        } catch (_: SQLiteConstraintException) {
            false
        }
    }

    override suspend fun adjustQuantity(productId: String, delta: Int) {
        val existing = productDao.getById(productId) ?: return
        val newQty = (existing.quantity + delta).coerceAtLeast(0)
        val newStatus = if (newQty == 0) ProductStatus.SOLD else ProductStatus.AVAILABLE
        val now = System.currentTimeMillis()
        productDao.updateQuantityAndStatus(productId, newQty, newStatus, now)
        syncEnqueuer.enqueueProduct(
            existing.copy(quantity = newQty, status = newStatus, updated_at = now),
        )
    }

    override suspend fun setStatus(productId: String, status: ProductStatus) {
        val existing = productDao.getById(productId) ?: return
        val now = System.currentTimeMillis()
        productDao.updateQuantityAndStatus(productId, existing.quantity, status, now)
        syncEnqueuer.enqueueProduct(existing.copy(status = status, updated_at = now))
    }

    override suspend fun renameId(oldId: String, newId: String) {
        productDao.renameProductId(oldId, newId)
        productDao.getById(newId)?.let { syncEnqueuer.enqueueProduct(it, SyncAction.UPDATE) }
    }

    override suspend fun delete(id: String) {
        val existing = productDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        productDao.softDelete(id, now)
        syncEnqueuer.enqueueProduct(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }

    override suspend fun restore(id: String) {
        val now = System.currentTimeMillis()
        productDao.restore(id, now)
        productDao.getByIdAnyState(id)?.let { syncEnqueuer.enqueueProduct(it, SyncAction.UPDATE) }
    }

    override fun observeCountsPerCategory(): Flow<Map<String, Int>> =
        productDao.observeCountsPerCategory().map { list -> list.associate { it.categoryId to it.count } }
}
