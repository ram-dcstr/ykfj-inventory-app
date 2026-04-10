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
}
