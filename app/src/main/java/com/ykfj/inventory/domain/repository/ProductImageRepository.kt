package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.ProductImage
import kotlinx.coroutines.flow.Flow

/**
 * Metadata-only repository — the actual JPEG files are written/read by
 * `ImageStorageManager` in the data layer. One image per product
 * (enforced by a unique index on `product_id`).
 */
interface ProductImageRepository {

    fun observeForProduct(productId: String): Flow<ProductImage?>

    suspend fun getForProduct(productId: String): ProductImage?

    suspend fun upsert(image: ProductImage)

    suspend fun delete(imageId: String)
}
