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

    /**
     * Melted records: the damaged record was soft-deleted AND the parent product
     * was also soft-deleted. The JOIN distinguishes melt (both deleted) from a
     * plain revert (only the damaged record is soft-deleted, product is restored).
     */
    @Query(
        """
        SELECT dr.* FROM damaged_records dr
        INNER JOIN products p ON p.product_id = dr.product_id
        WHERE dr.is_deleted = 1 AND dr.is_archived = 0 AND p.is_deleted = 1
        ORDER BY dr.date_recorded DESC
        """,
    )
    fun observeMelted(): Flow<List<DamagedRecordEntity>>

    @Query(
        """
        SELECT * FROM damaged_records
        WHERE is_deleted = 0 AND is_archived = 1
        ORDER BY date_recorded DESC
        """,
    )
    fun observeArchived(): Flow<List<DamagedRecordEntity>>

    @Query(
        """
        SELECT * FROM damaged_records
        WHERE is_deleted = 0 AND product_id = :productId
        ORDER BY date_recorded DESC
        """,
    )
    fun observeForProduct(productId: String): Flow<List<DamagedRecordEntity>>

    @Query(
        """
        SELECT * FROM damaged_records
        WHERE is_deleted = 0 AND product_id = :productId
        ORDER BY date_recorded DESC
        LIMIT 1
        """,
    )
    suspend fun getMostRecentForProduct(productId: String): DamagedRecordEntity?

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

    @Query(
        """
        UPDATE damaged_records
        SET is_deleted = 0, updated_at = :now WHERE damaged_id = :damagedId
        """,
    )
    suspend fun restore(damagedId: String, now: Long)

    @Query("SELECT * FROM damaged_records WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<DamagedRecordEntity>

    @Query(
        """
        SELECT * FROM damaged_records
        WHERE is_deleted = 0 AND is_archived = 1
          AND date_recorded BETWEEN :startMillis AND :endMillis
        ORDER BY date_recorded ASC
        """,
    )
    suspend fun getArchivedInRange(startMillis: Long, endMillis: Long): List<DamagedRecordEntity>

    @Query(
        """
        DELETE FROM damaged_records
        WHERE is_archived = 1 AND date_recorded BETWEEN :startMillis AND :endMillis
        """,
    )
    suspend fun hardDeleteArchivedInRange(startMillis: Long, endMillis: Long): Int
}
