package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.SoldRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoldRecordDao {

    /** Active (non-archived) sales only — list views hide archived rows by default. */
    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND is_archived = 0
        ORDER BY sold_date DESC
        """,
    )
    fun observeActive(): Flow<List<SoldRecordEntity>>

    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND is_archived = 0
          AND sold_date BETWEEN :startMillis AND :endMillis
        ORDER BY sold_date DESC
        """,
    )
    fun observeBetween(startMillis: Long, endMillis: Long): Flow<List<SoldRecordEntity>>

    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND is_archived = 1
        ORDER BY sold_date DESC
        """,
    )
    fun observeArchived(): Flow<List<SoldRecordEntity>>

    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND customer_id = :customerId
        ORDER BY sold_date DESC
        """,
    )
    fun observeByCustomer(customerId: String): Flow<List<SoldRecordEntity>>

    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND product_id = :productId
        ORDER BY sold_date DESC
        """,
    )
    suspend fun getByProduct(productId: String): List<SoldRecordEntity>

    @Query("SELECT * FROM sold_records WHERE sold_id = :soldId LIMIT 1")
    suspend fun getById(soldId: String): SoldRecordEntity?

    @Query(
        """
        SELECT COUNT(*) FROM sold_records
        WHERE product_id = :productId AND is_deleted = 0
        """,
    )
    suspend fun countActiveForProduct(productId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: SoldRecordEntity)

    @Update
    suspend fun update(record: SoldRecordEntity)

    @Query("UPDATE sold_records SET is_archived = 1, updated_at = :now WHERE sold_id = :soldId")
    suspend fun archive(soldId: String, now: Long)

    @Query("UPDATE sold_records SET is_deleted = 1, updated_at = :now WHERE sold_id = :soldId")
    suspend fun softDelete(soldId: String, now: Long)

    @Query("SELECT * FROM sold_records WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<SoldRecordEntity>
}
