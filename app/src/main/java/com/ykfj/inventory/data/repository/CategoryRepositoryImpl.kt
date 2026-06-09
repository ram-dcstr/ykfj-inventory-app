package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.CategoryDao
import com.ykfj.inventory.data.local.db.dao.ProductDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.repository.CategoryRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
    private val syncEnqueuer: SyncEnqueuer,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Category? =
        categoryDao.getById(id)?.toDomain()

    override suspend fun upsert(category: Category) {
        val entity = category.toEntity()
        val existing = categoryDao.getById(category.id)
        if (existing == null) {
            categoryDao.insert(entity)
            syncEnqueuer.enqueueCategory(entity, SyncAction.INSERT)
        } else {
            categoryDao.update(entity)
            syncEnqueuer.enqueueCategory(entity, SyncAction.UPDATE)
        }
    }

    override suspend fun delete(id: String) {
        val existing = categoryDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        categoryDao.softDelete(id, now)
        syncEnqueuer.enqueueCategory(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }

    override suspend fun countActiveProducts(categoryId: String): Int =
        productDao.countByCategory(categoryId)
}
