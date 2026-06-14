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
     * Count of UNPAID payments whose computed due-date falls in [dayStart, dayEnd]
     * across all active, non-archived groups. Powers the red badge on the
     * Paluwagan sidebar item.
     *
     * Due date is derived from the parent group as
     * `start_date + (round_number - 1) * frequency_days * 86_400_000ms`,
     * since the payment row itself doesn't store a due date — `payment_date`
     * is when the payment was actually received (null while UNPAID).
     */
    @Query(
        """
        SELECT COUNT(*) FROM paluwagan_payments p
        JOIN paluwagan_groups g ON g.group_id = p.group_id
        WHERE p.is_deleted = 0 AND p.status = 'UNPAID'
          AND g.is_deleted = 0 AND g.is_archived = 0 AND g.status = 'ACTIVE'
          AND (g.start_date + (p.round_number - 1) * g.frequency_days * 86400000)
              BETWEEN :dayStart AND :dayEnd
        """,
    )
    fun observeDueTodayCount(dayStart: Long, dayEnd: Long): Flow<Int>

    /**
     * Daily Cash (Phase 11): total contributions actually received on a day through
     * a given payment channel — money coming IN to the drawer.
     *
     * Filters on [payment_date] (when the cash was received), so a round prepaid weeks
     * early lands on the day it was paid, not the day it matures. Summing [amount_paid]
     * across the day's rows reconciles to the real cash taken, because an advance lump
     * is split across its rows (main remainder + one contribution per seeded round) with
     * a shared payment_date. Legacy rows with a null channel are treated as CASH, matching
     * the sold/layaway default. Returns 0.0 when no rows match.
     */
    @Query(
        """
        SELECT COALESCE(SUM(amount_paid), 0.0) FROM paluwagan_payments
        WHERE is_deleted = 0
          AND status != 'UNPAID'
          AND COALESCE(payment_channel, 'CASH') = :channel
          AND payment_date BETWEEN :startMillis AND :endMillis
        """,
    )
    fun observeContributionSumByChannelForDay(
        channel: String,
        startMillis: Long,
        endMillis: Long,
    ): Flow<Double>

    @Query("SELECT * FROM paluwagan_payments WHERE payment_id = :paymentId LIMIT 1")
    suspend fun getById(paymentId: String): PaluwaganPaymentEntity?

    @Query(
        """
        SELECT * FROM paluwagan_payments
        WHERE slot_id = :slotId AND round_number = :roundNumber AND is_deleted = 0
        LIMIT 1
        """,
    )
    suspend fun getForSlotAndRound(slotId: String, roundNumber: Int): PaluwaganPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(payment: PaluwaganPaymentEntity)

    @Update
    suspend fun update(payment: PaluwaganPaymentEntity)

    @Query(
        """
        UPDATE paluwagan_payments
        SET status = :status, payment_date = :paymentDate,
            amount_paid = :amountPaid, payment_channel = :paymentChannel,
            notes = :notes, updated_at = :now
        WHERE payment_id = :paymentId
        """,
    )
    suspend fun updateStatus(
        paymentId: String,
        status: PaluwaganPaymentStatus,
        paymentDate: Long,
        amountPaid: Double,
        paymentChannel: String?,
        notes: String?,
        now: Long,
    )

    /** Full edit — also updates notes; used by admin to correct a recorded payment. */
    @Query(
        """
        UPDATE paluwagan_payments
        SET status = :status, payment_date = :paymentDate, amount_paid = :amountPaid,
            payment_channel = :paymentChannel, notes = :notes, updated_at = :now
        WHERE payment_id = :paymentId
        """,
    )
    suspend fun updatePaymentFull(
        paymentId: String,
        status: PaluwaganPaymentStatus,
        paymentDate: Long,
        amountPaid: Double,
        paymentChannel: String?,
        notes: String?,
        now: Long,
    )

    @Query(
        """
        UPDATE paluwagan_payments
        SET is_deleted = 1, updated_at = :now WHERE payment_id = :paymentId
        """,
    )
    suspend fun softDelete(paymentId: String, now: Long)

    @Query("DELETE FROM paluwagan_payments WHERE group_id = :groupId")
    suspend fun hardDeleteByGroup(groupId: String)

    @Query("SELECT * FROM paluwagan_payments WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<PaluwaganPaymentEntity>
}
