package com.ykfj.inventory.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.ProductEntity
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProductDao {

    data class CategoryCount(
        @ColumnInfo(name = "category_id") val categoryId: String,
        val count: Int,
    )

    @Query("SELECT category_id, COUNT(*) AS count FROM products WHERE is_deleted = 0 GROUP BY category_id")
    abstract fun observeCountsPerCategory(): Flow<List<CategoryCount>>

    /** Default list view: newest first, SOLD / LAYAWAY / DAMAGED hidden. */
    @Query(
        """
        SELECT * FROM products
        WHERE is_deleted = 0 AND status NOT IN ('SOLD', 'LAYAWAY', 'DAMAGED')
        ORDER BY date_acquired DESC
        """,
    )
    abstract fun pagingAvailable(): PagingSource<Int, ProductEntity>

    /** Includes SOLD rows but still hides LAYAWAY and DAMAGED — used when the "show sold" filter is on. */
    @Query(
        """
        SELECT * FROM products
        WHERE is_deleted = 0 AND status NOT IN ('LAYAWAY', 'DAMAGED')
        ORDER BY date_acquired DESC
        """,
    )
    abstract fun pagingAll(): PagingSource<Int, ProductEntity>

    @Query(
        """
        SELECT * FROM products
        WHERE is_deleted = 0 AND status = :status
        ORDER BY date_acquired DESC
        """,
    )
    abstract fun pagingByStatus(status: ProductStatus): PagingSource<Int, ProductEntity>

    @Query(
        """
        SELECT * FROM products
        WHERE is_deleted = 0 AND category_id = :categoryId
        ORDER BY date_acquired DESC
        """,
    )
    abstract fun pagingByCategory(categoryId: String): PagingSource<Int, ProductEntity>

    /**
     * FTS-backed search. Joins `products_fts` MATCH results back to the
     * full row. Caller supplies a sanitised query token (e.g. "gold*").
     */
    @Query(
        """
        SELECT p.* FROM products p
        JOIN products_fts fts ON fts.rowid = p.rowid
        WHERE p.is_deleted = 0 AND p.status NOT IN ('LAYAWAY', 'DAMAGED') AND products_fts MATCH :query
        ORDER BY p.date_acquired DESC
        """,
    )
    abstract fun searchPaging(query: String): PagingSource<Int, ProductEntity>

    @Query("SELECT * FROM products WHERE product_id = :productId AND is_deleted = 0 LIMIT 1")
    abstract suspend fun getById(productId: String): ProductEntity?

    /** Lookup that does NOT filter `is_deleted` — used by history screens (e.g. melted items). */
    @Query("SELECT * FROM products WHERE product_id = :productId LIMIT 1")
    abstract suspend fun getByIdAnyState(productId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE product_id = :productId AND is_deleted = 0")
    abstract fun observeById(productId: String): Flow<ProductEntity?>

    /** Used by [ProductIdGenerator] to find the next sequence number. */
    @Query(
        """
        SELECT COUNT(*) FROM products
        WHERE name = :name
          AND (metal_rate_id IS :metalRateId OR metal_rate_id = :metalRateId)
          AND category_id = :categoryId
        """,
    )
    abstract suspend fun countByIdComponents(
        name: String,
        metalRateId: String?,
        categoryId: String,
    ): Int

    @Query("SELECT COUNT(*) FROM products WHERE category_id = :categoryId AND is_deleted = 0")
    abstract suspend fun countByCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM products WHERE metal_rate_id = :rateId AND is_deleted = 0")
    abstract suspend fun countByMetalRate(rateId: String): Int

    @Query("SELECT COUNT(*) FROM products WHERE supplier_id = :supplierId AND is_deleted = 0")
    abstract suspend fun countBySupplier(supplierId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(product: ProductEntity)

    @Update
    abstract suspend fun update(product: ProductEntity)

    @Query(
        """
        UPDATE products
        SET quantity = :quantity, status = :status, updated_at = :now
        WHERE product_id = :productId
        """,
    )
    abstract suspend fun updateQuantityAndStatus(
        productId: String,
        quantity: Int,
        status: ProductStatus,
        now: Long,
    )

    @Query("UPDATE products SET is_deleted = 1, updated_at = :now WHERE product_id = :productId")
    abstract suspend fun softDelete(productId: String, now: Long)

    @Query("UPDATE products SET is_deleted = 0, updated_at = :now WHERE product_id = :productId")
    abstract suspend fun restore(productId: String, now: Long)

    @Query("SELECT * FROM products WHERE updated_at > :since")
    abstract suspend fun getChangedSince(since: Long): List<ProductEntity>

    // ── Rename helpers (used when name/rate/category changes on edit) ──────────

    @Query("UPDATE products SET product_id = :newId, updated_at = :now WHERE product_id = :oldId")
    abstract suspend fun updateProductId(oldId: String, newId: String, now: Long)

    @Query("UPDATE sold_records SET product_id = :newId WHERE product_id = :oldId")
    abstract suspend fun renameSoldRecordProductId(oldId: String, newId: String)

    @Query("UPDATE layaway_records SET product_id = :newId WHERE product_id = :oldId")
    abstract suspend fun renameLayawayRecordProductId(oldId: String, newId: String)

    @Query("UPDATE damaged_records SET product_id = :newId WHERE product_id = :oldId")
    abstract suspend fun renameDamagedRecordProductId(oldId: String, newId: String)

    @Query("UPDATE product_images SET product_id = :newId WHERE product_id = :oldId")
    abstract suspend fun renameProductImageProductId(oldId: String, newId: String)

    /** Atomically renames a product ID across all tables that reference it. */
    @Transaction
    open suspend fun renameProductId(oldId: String, newId: String) {
        val now = System.currentTimeMillis()
        updateProductId(oldId, newId, now)
        renameSoldRecordProductId(oldId, newId)
        renameLayawayRecordProductId(oldId, newId)
        renameDamagedRecordProductId(oldId, newId)
        renameProductImageProductId(oldId, newId)
    }
}
