package com.ykfj.inventory.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.ProductEntity
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    /** Default list view: newest first, SOLD hidden. */
    @Query(
        """
        SELECT * FROM products
        WHERE is_deleted = 0 AND status != 'SOLD'
        ORDER BY date_acquired DESC
        """,
    )
    fun pagingAvailable(): PagingSource<Int, ProductEntity>

    /** Includes SOLD rows — used when the "show sold" filter is on. */
    @Query(
        """
        SELECT * FROM products
        WHERE is_deleted = 0
        ORDER BY date_acquired DESC
        """,
    )
    fun pagingAll(): PagingSource<Int, ProductEntity>

    @Query(
        """
        SELECT * FROM products
        WHERE is_deleted = 0 AND status = :status
        ORDER BY date_acquired DESC
        """,
    )
    fun pagingByStatus(status: ProductStatus): PagingSource<Int, ProductEntity>

    @Query(
        """
        SELECT * FROM products
        WHERE is_deleted = 0 AND category_id = :categoryId
        ORDER BY date_acquired DESC
        """,
    )
    fun pagingByCategory(categoryId: String): PagingSource<Int, ProductEntity>

    /**
     * FTS-backed search. Joins `products_fts` MATCH results back to the
     * full row. Caller supplies a sanitised query token (e.g. "gold*").
     */
    @Query(
        """
        SELECT p.* FROM products p
        JOIN products_fts fts ON fts.rowid = p.rowid
        WHERE p.is_deleted = 0 AND products_fts MATCH :query
        ORDER BY p.date_acquired DESC
        """,
    )
    fun searchPaging(query: String): PagingSource<Int, ProductEntity>

    @Query("SELECT * FROM products WHERE product_id = :productId AND is_deleted = 0 LIMIT 1")
    suspend fun getById(productId: String): ProductEntity?

    @Query("SELECT * FROM products WHERE product_id = :productId AND is_deleted = 0")
    fun observeById(productId: String): Flow<ProductEntity?>

    /** Used by [ProductIdGenerator] to find the next sequence number. */
    @Query(
        """
        SELECT COUNT(*) FROM products
        WHERE name = :name
          AND (metal_rate_id IS :metalRateId OR metal_rate_id = :metalRateId)
          AND category_id = :categoryId
        """,
    )
    suspend fun countByIdComponents(
        name: String,
        metalRateId: String?,
        categoryId: String,
    ): Int

    @Query("SELECT COUNT(*) FROM products WHERE category_id = :categoryId AND is_deleted = 0")
    suspend fun countByCategory(categoryId: String): Int

    @Query("SELECT COUNT(*) FROM products WHERE metal_rate_id = :rateId AND is_deleted = 0")
    suspend fun countByMetalRate(rateId: String): Int

    @Query("SELECT COUNT(*) FROM products WHERE supplier_id = :supplierId AND is_deleted = 0")
    suspend fun countBySupplier(supplierId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(product: ProductEntity)

    @Update
    suspend fun update(product: ProductEntity)

    @Query(
        """
        UPDATE products
        SET quantity = :quantity, status = :status, updated_at = :now
        WHERE product_id = :productId
        """,
    )
    suspend fun updateQuantityAndStatus(
        productId: String,
        quantity: Int,
        status: ProductStatus,
        now: Long,
    )

    @Query("UPDATE products SET is_deleted = 1, updated_at = :now WHERE product_id = :productId")
    suspend fun softDelete(productId: String, now: Long)

    @Query("SELECT * FROM products WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<ProductEntity>
}
