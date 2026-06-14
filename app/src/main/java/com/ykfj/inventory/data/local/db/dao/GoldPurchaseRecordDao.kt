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

    /**
     * Live record for a detail screen. Filters [is_deleted] so a soft-delete
     * arriving via sync flips the screen to a "not found" state.
     */
    @Query("SELECT * FROM gold_purchase_records WHERE id = :id AND is_deleted = 0 LIMIT 1")
    fun observeById(id: String): Flow<GoldPurchaseRecordEntity?>

    /**
     * One-shot fetch. Intentionally does NOT filter [is_deleted] because the
     * sync merge path needs to see soft-deleted local rows so it can apply
     * incoming UPDATEs (including the DELETE-flagging UPDATE) on top of them.
     * Repo callers that hit this for fetch-before-mutate are operating on
     * not-yet-deleted rows, so the lack of filter is harmless for them.
     */
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

    /**
     * Daily Cash (Phase 11): total cash paid out for **straight gold purchases**
     * on a given day. Trade-in gold purchases (rows with `linked_sold_record_id`
     * set) are intentionally excluded — they're a barter against a sale, not a
     * cash outflow. The trade-in's economic impact is already netted into the
     * linked sale's payment-method bucket by
     * [SoldRecordDao.observeSumByPaymentMethodForDay].
     *
     * Returns 0.0 when no rows match.
     */
    @Query(
        """
        SELECT COALESCE(SUM(total_paid), 0.0) FROM gold_purchase_records
        WHERE is_deleted = 0
          AND linked_sold_record_id IS NULL
          AND paid_at BETWEEN :startMillis AND :endMillis
        """,
    )
    fun observeSumForDay(startMillis: Long, endMillis: Long): Flow<Double>
}
