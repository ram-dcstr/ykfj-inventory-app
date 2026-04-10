package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.DamagedRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DamagedRecordDao {

    @Query(
        """
        SELECT * FROM damaged_records
        WHERE is_deleted = 0 AND is_archived = 0
        ORDER BY date_recorded DESC
        """,
    )
    fun observeActive(): Flow<List<DamagedRecordEntity>>

    @Query(
        """
        SELECT * FROM damaged_records
        WHERE is_deleted = 0 AND is_archived = 1
        ORDER BY date_recorded DESC
        """,
    )
    fun observeArchived(): Flow<List<DamagedRecordEntity>>

    @Query("SELECT * FROM damaged_records WHERE damaged_id = :damagedId LIMIT 1")
    suspend fun getById(damagedId: String): DamagedRecordEntity?

    @Query(
        """
        SELECT COUNT(*) FROM damaged_records
        WHERE product_id = :productId AND is_deleted = 0
        """,
    )
    suspend fun countActiveForProduct(productId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: DamagedRecordEntity)

    @Update
    suspend fun update(record: DamagedRecordEntity)

    @Query(
        """
        UPDATE damaged_records
        SET is_archived = 1, updated_at = :now WHERE damaged_id = :damagedId
        """,
    )
    suspend fun archive(damagedId: String, now: Long)

    @Query(
        """
        UPDATE damaged_records
        SET is_deleted = 1, updated_at = :now WHERE damaged_id = :damagedId
        """,
    )
    suspend fun softDelete(damagedId: String, now: Long)

    @Query("SELECT * FROM damaged_records WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<DamagedRecordEntity>
}
