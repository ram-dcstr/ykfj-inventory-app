package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.PaluwaganPaymentEntity
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PaluwaganPaymentDao {

    @Query(
        """
        SELECT * FROM paluwagan_payments
        WHERE group_id = :groupId AND is_deleted = 0
        ORDER BY round_number ASC
        """,
    )
    fun observeForGroup(groupId: String): Flow<List<PaluwaganPaymentEntity>>

    @Query(
        """
        SELECT * FROM paluwagan_payments
        WHERE group_id = :groupId AND round_number = :round AND is_deleted = 0
        """,
    )
    fun observeForRound(groupId: String, round: Int): Flow<List<PaluwaganPaymentEntity>>

    @Query(
        """
        SELECT * FROM paluwagan_payments
        WHERE slot_id = :slotId AND is_deleted = 0
        ORDER BY round_number ASC
        """,
    )
    fun observeForSlot(slotId: String): Flow<List<PaluwaganPaymentEntity>>

    /**
     * Count of payments due today across all active groups. Powers the red
     * badge on the Paluwagan sidebar item.
     */
    @Query(
        """
        SELECT COUNT(*) FROM paluwagan_payments
        WHERE is_deleted = 0 AND status = 'UNPAID'
          AND payment_date IS NOT NULL
          AND payment_date BETWEEN :dayStart AND :dayEnd
        """,
    )
    fun observeDueTodayCount(dayStart: Long, dayEnd: Long): Flow<Int>

    @Query("SELECT * FROM paluwagan_payments WHERE payment_id = :paymentId LIMIT 1")
    suspend fun getById(paymentId: String): PaluwaganPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaluwaganPaymentEntity)

    @Update
    suspend fun update(payment: PaluwaganPaymentEntity)

    @Query(
        """
        UPDATE paluwagan_payments
        SET status = :status, payment_date = :paymentDate,
            amount_paid = :amountPaid, updated_at = :now
        WHERE payment_id = :paymentId
        """,
    )
    suspend fun updateStatus(
        paymentId: String,
        status: PaluwaganPaymentStatus,
        paymentDate: Long?,
        amountPaid: Double,
        now: Long,
    )

    @Query(
        """
        UPDATE paluwagan_payments
        SET is_deleted = 1, updated_at = :now WHERE payment_id = :paymentId
        """,
    )
    suspend fun softDelete(paymentId: String, now: Long)

    @Query("SELECT * FROM paluwagan_payments WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<PaluwaganPaymentEntity>
}
