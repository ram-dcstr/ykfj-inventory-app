package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.ProductImageDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.ProductImage
import com.ykfj.inventory.domain.repository.ProductImageRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ProductImageRepositoryImpl @Inject constructor(
    private val productImageDao: ProductImageDao,
    private val syncEnqueuer: SyncEnqueuer,
) : ProductImageRepository {

    override fun observeThumbMap(): Flow<Map<String, String>> =
        productImageDao.observeAll().map { list -> list.associate { it.product_id to it.file_name } }

    override fun observeForProduct(productId: String): Flow<ProductImage?> =
        productImageDao.observeByProduct(productId).map { it?.toDomain() }

    override suspend fun getForProduct(productId: String): ProductImage? =
        productImageDao.getByProduct(productId)?.toDomain()

    override suspend fun upsert(image: ProductImage) {
        val entity = image.toEntity()
        productImageDao.upsert(entity)
        syncEnqueuer.enqueueProductImage(entity, SyncAction.UPDATE)
    }

    override suspend fun delete(imageId: String) {
        val existing = productImageDao.getById(imageId) ?: return
        val now = System.currentTimeMillis()
        productImageDao.softDelete(imageId, now)
        syncEnqueuer.enqueueProductImage(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }
}
