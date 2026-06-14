package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.StockAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockAdjustmentDao {

    @Query(
        """
        SELECT * FROM stock_adjustments
        WHERE is_deleted = 0 AND is_archived = 0
        ORDER BY date_recorded DESC
        """,
    )
    fun observeActive(): Flow<List<StockAdjustmentEntity>>

    @Query(
        """
        SELECT * FROM stock_adjustments
        WHERE is_deleted = 0 AND product_id = :productId
        ORDER BY date_recorded DESC
        """,
    )
    fun observeForProduct(productId: String): Flow<List<StockAdjustmentEntity>>

    @Query("SELECT * FROM stock_adjustments WHERE adjustment_id = :adjustmentId LIMIT 1")
    suspend fun getById(adjustmentId: String): StockAdjustmentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: StockAdjustmentEntity)

    @Update
    suspend fun update(record: StockAdjustmentEntity)

    @Query("UPDATE stock_adjustments SET is_deleted = 1, updated_at = :now WHERE adjustment_id = :adjustmentId")
    suspend fun softDelete(adjustmentId: String, now: Long)

    @Query("SELECT * FROM stock_adjustments WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<StockAdjustmentEntity>
}
