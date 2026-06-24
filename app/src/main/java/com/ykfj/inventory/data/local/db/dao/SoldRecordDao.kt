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
    fun observeForProduct(productId: String): Flow<List<SoldRecordEntity>>

    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND product_id = :productId
        ORDER BY sold_date DESC
        LIMIT 1
        """,
    )
    suspend fun getMostRecentForProduct(productId: String): SoldRecordEntity?

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

    /** Finds the auto-generated SoldRecord created by completing a layaway (notes carry the marker). */
    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND notes = :notesMarker
        LIMIT 1
        """,
    )
    suspend fun findByNotes(notesMarker: String): SoldRecordEntity?

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

    /**
     * Daily Cash (Phase 11): sum of `sold_price * quantity` for a given payment
     * method on a given calendar day, **netted against any linked trade-in gold
     * purchase**. Excludes deleted and archived rows.
     *
     * Trade-in semantics: when a sale is paired with a gold purchase via
     * `gold_purchase_records.linked_sold_record_id`, the customer paid the
     * gross sale price only partially through this payment method — the rest
     * was paid in scrap. So the actual cash/GCash/etc. that came in via this
     * method is `(sold_price * quantity) − linked.total_paid`. Even swaps net
     * to 0, customer-paid-difference cases net to just the difference, and
     * shop-pays-out cases produce a negative contribution.
     *
     * Non-trade-in rows have no matching gold purchase → LEFT JOIN produces
     * NULL → COALESCE → 0 → row contributes its full gross. Returns 0.0 when
     * no rows match.
     */
    @Query(
        """
        SELECT s.payment_method AS method, COALESCE(SUM(
            (s.sold_price * s.quantity) - COALESCE(gp.total_paid, 0)
        ), 0.0) AS total
        FROM sold_records s
        LEFT JOIN gold_purchase_records gp
            ON gp.linked_sold_record_id = s.sold_id
            AND gp.is_deleted = 0
        WHERE s.is_deleted = 0 AND s.is_archived = 0
          AND s.sold_date BETWEEN :startMillis AND :endMillis
        GROUP BY s.payment_method
        """,
    )
    fun observeSumsByPaymentMethodForDay(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<PaymentMethodTotal>>

    /** Daily Cash: sales matching a payment method on a given day. Used to expand the row's detail list. */
    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND is_archived = 0
          AND payment_method = :paymentMethod
          AND sold_date BETWEEN :startMillis AND :endMillis
        ORDER BY sold_date DESC
        """,
    )
    fun observeByPaymentMethodForDay(
        paymentMethod: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<SoldRecordEntity>>

    @Query(
        """
        SELECT * FROM sold_records
        WHERE is_deleted = 0 AND is_archived = 1
          AND sold_date BETWEEN :startMillis AND :endMillis
        ORDER BY sold_date ASC
        """,
    )
    suspend fun getArchivedInRange(startMillis: Long, endMillis: Long): List<SoldRecordEntity>

    @Query(
        """
        DELETE FROM sold_records
        WHERE is_archived = 1 AND sold_date BETWEEN :startMillis AND :endMillis
        """,
    )
    suspend fun hardDeleteArchivedInRange(startMillis: Long, endMillis: Long): Int
}
