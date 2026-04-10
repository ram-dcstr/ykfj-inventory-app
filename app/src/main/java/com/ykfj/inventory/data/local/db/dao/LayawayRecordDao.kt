package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.LayawayRecordEntity
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface LayawayRecordDao {

    @Query(
        """
        SELECT * FROM layaway_records
        WHERE is_deleted = 0 AND is_archived = 0 AND status = 'ACTIVE'
        ORDER BY due_date ASC, created_at DESC
        """,
    )
    fun observeActive(): Flow<List<LayawayRecordEntity>>

    @Query(
        """
        SELECT * FROM layaway_records
        WHERE is_deleted = 0 AND is_archived = 0 AND customer_id = :customerId AND status = 'ACTIVE'
        ORDER BY created_at DESC
        """,
    )
    fun observeActiveForCustomer(customerId: String): Flow<List<LayawayRecordEntity>>

    @Query(
        """
        SELECT * FROM layaway_records
        WHERE is_deleted = 0 AND is_archived = 0 AND customer_id = :customerId
        ORDER BY created_at DESC
        """,
    )
    fun observeByCustomer(customerId: String): Flow<List<LayawayRecordEntity>>

    @Query(
        """
        SELECT * FROM layaway_records
        WHERE is_deleted = 0 AND is_archived = 1
        ORDER BY completion_date DESC
        """,
    )
    fun observeArchived(): Flow<List<LayawayRecordEntity>>

    /**
     * Overdue = ACTIVE, not archived, with a [due_date] strictly in the past.
     * Drives the red badge on the Layaway sidebar entry.
     */
    @Query(
        """
        SELECT COUNT(*) FROM layaway_records
        WHERE is_deleted = 0 AND is_archived = 0 AND status = 'ACTIVE'
          AND due_date IS NOT NULL AND due_date < :now
        """,
    )
    fun observeOverdueCount(now: Long): Flow<Int>

    @Query("SELECT * FROM layaway_records WHERE layaway_id = :layawayId LIMIT 1")
    suspend fun getById(layawayId: String): LayawayRecordEntity?

    @Query("SELECT * FROM layaway_records WHERE layaway_id = :layawayId")
    fun observeById(layawayId: String): Flow<LayawayRecordEntity?>

    @Query(
        """
        SELECT COUNT(*) FROM layaway_records
        WHERE product_id = :productId AND status = 'ACTIVE' AND is_deleted = 0
        """,
    )
    suspend fun countActiveForProduct(productId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: LayawayRecordEntity)

    @Update
    suspend fun update(record: LayawayRecordEntity)

    @Query(
        """
        UPDATE layaway_records
        SET total_paid = :totalPaid, updated_at = :now
        WHERE layaway_id = :layawayId
        """,
    )
    suspend fun updateTotalPaid(layawayId: String, totalPaid: Double, now: Long)

    @Query(
        """
        UPDATE layaway_records
        SET status = :status, completion_date = :completionDate,
            forfeited_amount = :forfeitedAmount, updated_at = :now
        WHERE layaway_id = :layawayId
        """,
    )
    suspend fun updateStatus(
        layawayId: String,
        status: LayawayStatus,
        completionDate: Long?,
        forfeitedAmount: Double?,
        now: Long,
    )

    @Query(
        """
        UPDATE layaway_records
        SET is_archived = 1, updated_at = :now WHERE layaway_id = :layawayId
        """,
    )
    suspend fun archive(layawayId: String, now: Long)

    @Query(
        """
        UPDATE layaway_records
        SET is_deleted = 1, updated_at = :now WHERE layaway_id = :layawayId
        """,
    )
    suspend fun softDelete(layawayId: String, now: Long)

    @Query("SELECT * FROM layaway_records WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<LayawayRecordEntity>
}
