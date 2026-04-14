package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.CategoryDao
import com.ykfj.inventory.data.local.db.dao.ProductDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val productDao: ProductDao,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getById(id: String): Category? =
        categoryDao.getById(id)?.toDomain()

    override suspend fun upsert(category: Category) {
        val existing = categoryDao.getById(category.id)
        if (existing == null) {
            categoryDao.insert(category.toEntity())
        } else {
            categoryDao.update(category.toEntity())
        }
    }

    override suspend fun delete(id: String) {
        categoryDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun countActiveProducts(categoryId: String): Int =
        productDao.countByCategory(categoryId)
}
