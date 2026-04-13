package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {

    /** Observe active products (excludes soft-deleted). Default sort: newest first by date_acquired. */
    fun observeAll(): Flow<List<Product>>

    /** FTS4-backed search over name, product_id, notes. */
    fun search(query: String): Flow<List<Product>>

    suspend fun getById(id: String): Product?

    suspend fun upsert(product: Product)

    /**
     * Adjust available quantity by [delta] (negative to consume, positive to
     * restore). Flips [Product.status] to SOLD when the resulting quantity
     * reaches zero, back to AVAILABLE otherwise.
     */
    suspend fun adjustQuantity(productId: String, delta: Int)

    /**
     * Soft delete. Callers must ensure no sold/layaway/damaged records
     * reference this product — see the deletion guard in `Inventory-Rules.md`.
     */
    suspend fun delete(id: String)
}
