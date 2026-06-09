package com.ykfj.inventory.domain.repository

import androidx.paging.PagingData
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.Product
import kotlinx.coroutines.flow.Flow

interface ProductRepository {

    /**
     * Paginated product list, newest first.
     * When [showSold] is false (default), sold products are hidden.
     */
    fun observeProductsPaged(showSold: Boolean = false): Flow<PagingData<Product>>

    /** FTS4-backed paginated search over name, product_id, notes. */
    fun searchProductsPaged(query: String): Flow<PagingData<Product>>

    suspend fun getById(id: String): Product?

    /** Lookup that ignores `is_deleted` — used by history views (e.g. melted scraps) that need the original name. */
    suspend fun getByIdAnyState(id: String): Product?

    /** Observe a single product — emits null if deleted. Used by the detail screen for live updates. */
    fun observeById(id: String): Flow<Product?>

    suspend fun upsert(product: Product)

    /**
     * Adjust available quantity by [delta] (negative to consume, positive to
     * restore). Flips [Product.status] to SOLD when the resulting quantity
     * reaches zero, back to AVAILABLE otherwise.
     */
    suspend fun adjustQuantity(productId: String, delta: Int)

    /** Override product status directly — used after adjust-quantity when the action type differs from SOLD. */
    suspend fun setStatus(productId: String, status: ProductStatus)

    /**
     * Atomically renames a product ID in [products] and all child tables
     * (sold_records, layaway_records, damaged_records, product_images).
     * Called when name / metal rate / category changes on edit, since the
     * product ID encodes those three components.
     */
    suspend fun renameId(oldId: String, newId: String)

    /**
     * Soft delete. Callers must ensure no sold/layaway/damaged records
     * reference this product — see the deletion guard in `Inventory-Rules.md`.
     */
    suspend fun delete(id: String)

    /** Inverse of [delete] — flips `is_deleted` back to 0. Used by the melt-revert flow. */
    suspend fun restore(id: String)

    /** Live count of non-deleted products per category. Used by the Categories screen. */
    fun observeCountsPerCategory(): Flow<Map<String, Int>>
}
