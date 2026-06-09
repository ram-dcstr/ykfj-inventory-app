package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ykfj.inventory.data.local.db.entity.GoldPurchaseItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoldPurchaseItemDao {

    @Query(
        """
        SELECT * FROM gold_purchase_items
        WHERE is_deleted = 0
        ORDER BY created_at DESC
        """,
    )
    fun observeAll(): Flow<List<GoldPurchaseItemEntity>>

    @Query(
        """
        SELECT * FROM gold_purchase_items
        WHERE is_deleted = 0 AND purchase_record_id = :purchaseRecordId
        ORDER BY created_at ASC
        """,
    )
    fun observeForRecord(purchaseRecordId: String): Flow<List<GoldPurchaseItemEntity>>

    @Query(
        """
        SELECT * FROM gold_purchase_items
        WHERE is_deleted = 0 AND purchase_record_id = :purchaseRecordId
        ORDER BY created_at ASC
        """,
    )
    suspend fun getForRecord(purchaseRecordId: String): List<GoldPurchaseItemEntity>

    @Query("SELECT COUNT(*) FROM gold_purchase_items WHERE is_deleted = 0 AND purchase_record_id = :purchaseRecordId")
    suspend fun countForRecord(purchaseRecordId: String): Int

    @Query("SELECT * FROM gold_purchase_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GoldPurchaseItemEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: GoldPurchaseItemEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<GoldPurchaseItemEntity>)

    @androidx.room.Update
    suspend fun update(item: GoldPurchaseItemEntity)

    @Query("UPDATE gold_purchase_items SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query(
        """
        UPDATE gold_purchase_items
        SET sold_to_supplier_at = :soldAt,
            sold_to_supplier_price = :price,
            updated_at = :now
        WHERE id = :id
        """,
    )
    suspend fun markSoldToSupplier(id: String, price: Double, soldAt: Long, now: Long)

    @Query(
        """
        UPDATE gold_purchase_items
        SET sold_to_supplier_at = NULL,
            sold_to_supplier_price = NULL,
            updated_at = :now
        WHERE id = :id
        """,
    )
    suspend fun unmarkSoldToSupplier(id: String, now: Long)

    @Query(
        """
        SELECT COALESCE(SUM(sold_to_supplier_price - final_value), 0.0)
        FROM gold_purchase_items
        WHERE is_deleted = 0
          AND sold_to_supplier_at BETWEEN :start AND :end
        """,
    )
    fun observeSupplierProfit(start: Long, end: Long): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(sold_to_supplier_price), 0.0)
        FROM gold_purchase_items
        WHERE is_deleted = 0
          AND sold_to_supplier_at BETWEEN :start AND :end
        """,
    )
    fun observeSupplierRevenue(start: Long, end: Long): Flow<Double>

    @Query(
        """
        SELECT COUNT(*)
        FROM gold_purchase_items
        WHERE is_deleted = 0
          AND sold_to_supplier_at BETWEEN :start AND :end
        """,
    )
    fun observeSupplierSoldCount(start: Long, end: Long): Flow<Int>

    @Query(
        """
        UPDATE gold_purchase_items
        SET is_deleted = 1, updated_at = :now
        WHERE purchase_record_id = :purchaseRecordId
        """,
    )
    suspend fun softDeleteForRecord(purchaseRecordId: String, now: Long)

    @Query("SELECT * FROM gold_purchase_items WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<GoldPurchaseItemEntity>
}
