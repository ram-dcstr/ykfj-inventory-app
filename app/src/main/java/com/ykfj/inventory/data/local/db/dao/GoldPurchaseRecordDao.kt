package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.GoldPurchaseRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoldPurchaseRecordDao {

    @Query(
        """
        SELECT * FROM gold_purchase_records
        WHERE is_deleted = 0
        ORDER BY paid_at DESC
        """,
    )
    fun observeAll(): Flow<List<GoldPurchaseRecordEntity>>

    @Query(
        """
        SELECT * FROM gold_purchase_records
        WHERE is_deleted = 0 AND customer_id = :customerId
        ORDER BY paid_at DESC
        """,
    )
    fun observeByCustomer(customerId: String): Flow<List<GoldPurchaseRecordEntity>>

    @Query(
        """
        SELECT * FROM gold_purchase_records
        WHERE is_deleted = 0
          AND paid_at BETWEEN :startMillis AND :endMillis
        ORDER BY paid_at DESC
        """,
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<GoldPurchaseRecordEntity>>

    @Query("SELECT * FROM gold_purchase_records WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<GoldPurchaseRecordEntity?>

    @Query("SELECT * FROM gold_purchase_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): GoldPurchaseRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: GoldPurchaseRecordEntity)

    @Update
    suspend fun update(record: GoldPurchaseRecordEntity)

    @Query("UPDATE gold_purchase_records SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM gold_purchase_records WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<GoldPurchaseRecordEntity>
}
