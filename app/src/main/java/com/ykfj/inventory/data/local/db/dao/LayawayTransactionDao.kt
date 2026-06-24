package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.LayawayTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LayawayTransactionDao {

    @Query(
        """
        SELECT * FROM layaway_transactions
        WHERE layaway_id = :layawayId AND is_deleted = 0
        ORDER BY payment_date DESC
        """,
    )
    fun observeForLayaway(layawayId: String): Flow<List<LayawayTransactionEntity>>

    /** Batch one-shot lookup for many layaways — avoids opening a Flow per record. */
    @Query(
        """
        SELECT * FROM layaway_transactions
        WHERE layaway_id IN (:layawayIds) AND is_deleted = 0
        ORDER BY payment_date DESC
        """,
    )
    suspend fun getForLayaways(layawayIds: List<String>): List<LayawayTransactionEntity>

    @Query("SELECT * FROM layaway_transactions WHERE transaction_id = :transactionId LIMIT 1")
    suspend fun getById(transactionId: String): LayawayTransactionEntity?

    @Query(
        """
        SELECT COALESCE(SUM(amount_paid), 0.0) FROM layaway_transactions
        WHERE layaway_id = :layawayId AND is_deleted = 0
        """,
    )
    suspend fun sumForLayaway(layawayId: String): Double

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: LayawayTransactionEntity)

    @Update
    suspend fun update(transaction: LayawayTransactionEntity)

    @Query(
        """
        UPDATE layaway_transactions
        SET is_deleted = 1, updated_at = :now WHERE transaction_id = :transactionId
        """,
    )
    suspend fun softDelete(transactionId: String, now: Long)

    @Query("SELECT * FROM layaway_transactions WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<LayawayTransactionEntity>

    /**
     * Daily Cash (Phase 11): sum of layaway payments matching a payment method
     * on a given calendar day. Returns 0.0 when no rows match.
     */
    @Query(
        """
        SELECT payment_method AS method, COALESCE(SUM(amount_paid), 0.0) AS total
        FROM layaway_transactions
        WHERE is_deleted = 0
          AND payment_date BETWEEN :startMillis AND :endMillis
        GROUP BY payment_method
        """,
    )
    fun observeSumsByPaymentMethodForDay(
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<PaymentMethodTotal>>

    /** Daily Cash: layaway payments matching a payment method on a given day. */
    @Query(
        """
        SELECT * FROM layaway_transactions
        WHERE is_deleted = 0
          AND payment_method = :paymentMethod
          AND payment_date BETWEEN :startMillis AND :endMillis
        ORDER BY payment_date DESC
        """,
    )
    fun observeByPaymentMethodForDay(
        paymentMethod: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<List<LayawayTransactionEntity>>
}
