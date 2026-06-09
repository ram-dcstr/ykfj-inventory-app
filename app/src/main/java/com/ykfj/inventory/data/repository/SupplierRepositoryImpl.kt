package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.ProductDao
import com.ykfj.inventory.data.local.db.dao.SupplierDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.Supplier
import com.ykfj.inventory.domain.repository.SupplierRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SupplierRepositoryImpl @Inject constructor(
    private val supplierDao: SupplierDao,
    private val productDao: ProductDao,
    private val syncEnqueuer: SyncEnqueuer,
) : SupplierRepository {

    override fun observeAll(): Flow<List<Supplier>> =
        supplierDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Supplier? =
        supplierDao.getById(id)?.toDomain()

    override suspend fun upsert(supplier: Supplier) {
        val entity = supplier.toEntity()
        val existing = supplierDao.getById(supplier.id)
        if (existing == null) {
            supplierDao.insert(entity)
            syncEnqueuer.enqueueSupplier(entity, SyncAction.INSERT)
        } else {
            supplierDao.update(entity)
            syncEnqueuer.enqueueSupplier(entity, SyncAction.UPDATE)
        }
    }

    override suspend fun delete(id: String) {
        val existing = supplierDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        supplierDao.softDelete(id, now)
        syncEnqueuer.enqueueSupplier(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }

    override suspend fun countActiveProducts(supplierId: String): Int =
        productDao.countBySupplier(supplierId)
}
