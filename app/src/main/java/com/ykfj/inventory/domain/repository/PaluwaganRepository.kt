package com.ykfj.inventory.domain.repository

import androidx.paging.PagingData
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.PaluwaganGroup
import com.ykfj.inventory.domain.model.PaluwaganPayment
import com.ykfj.inventory.domain.model.PaluwaganSlot
import kotlinx.coroutines.flow.Flow

/**
 * Describes a single payment write — either an insert ([existingPaymentId] null)
 * or an in-place update of an existing UNPAID row ([existingPaymentId] non-null).
 * Used by [PaluwaganRepository.recordPaymentAtomic] to batch all writes for a
 * payment session (main round + seeds) into a single database transaction.
 */
data class PaymentUpsert(
    /** Non-null = update this row in place; null = insert [payment] as a new row. */
    val existingPaymentId: String?,
    val payment: PaluwaganPayment,
)

/**
 * Owns all three paluwagan entities (group, slot, payment). They are
 * tightly coupled — advancing a round inserts payments for every slot;
 * swapping positions mutates two slots atomically — so sharing a
 * repository keeps those multi-row invariants in one place.
 */
interface PaluwaganRepository {

    fun observeActiveGroups(): Flow<List<PaluwaganGroup>>

    /**
     * Count of UNPAID payments whose computed due-date falls within
     * [dayStart] .. [dayEnd]. Powers the red badge on the Paluwagan sidebar
     * entry. Caller must pass refreshed day boundaries periodically.
     */
    fun observeDueTodayCount(dayStart: Long, dayEnd: Long): Flow<Int>

    fun observeGroup(groupId: String): Flow<PaluwaganGroup?>

    fun observeSlots(groupId: String): Flow<List<PaluwaganSlot>>

    fun observePayments(groupId: String): Flow<List<PaluwaganPayment>>

    fun observeSlotsForCustomer(customerId: String): Flow<List<PaluwaganSlot>>

    fun observePaymentsForSlot(slotId: String): Flow<List<PaluwaganPayment>>

    suspend fun getGroupById(groupId: String): PaluwaganGroup?

    suspend fun createGroup(group: PaluwaganGroup)

    suspend fun updateGroup(group: PaluwaganGroup)

    suspend fun addSlot(slot: PaluwaganSlot)

    /** Atomic swap of two slots' positions. */
    suspend fun swapPositions(slotIdA: String, slotIdB: String)

    suspend fun recordPayment(payment: PaluwaganPayment)

    /** Moves `current_round` forward, seeds UNPAID payment rows for each slot. */
    suspend fun advanceRound(groupId: String)

    /** Marks group COMPLETED when every round has been paid. */
    suspend fun completeGroup(groupId: String)

    suspend fun archiveGroup(groupId: String)

    suspend fun deleteGroup(groupId: String)

    /** Reassigns positions 1..n to each slot in the given order. */
    suspend fun reorderSlots(groupId: String, orderedSlotIds: List<String>)

    /** Pasalo: replaces the customer assigned to a slot. */
    suspend fun updateSlotCustomer(slotId: String, newCustomerId: String)

    /** Records the date and channel through which the collector received the pot money. */
    suspend fun recordPotCollection(slotId: String, date: Long, payoutChannel: PaymentMethod?)

    suspend fun getSlotsForGroup(groupId: String): List<PaluwaganSlot>

    suspend fun getSlotById(slotId: String): PaluwaganSlot?

    suspend fun getPaymentForSlotRound(slotId: String, roundNumber: Int): PaluwaganPayment?

    suspend fun updatePaymentStatus(
        paymentId: String,
        status: PaluwaganPaymentStatus,
        paymentDate: Long,
        amountPaid: Double,
        paymentMethod: PaymentMethod?,
        notes: String? = null,
    )

    /** Admin full edit of a recorded payment (also updates notes). */
    suspend fun updatePaymentFull(
        paymentId: String,
        status: PaluwaganPaymentStatus,
        paymentDate: Long,
        amountPaid: Double,
        paymentMethod: PaymentMethod?,
        notes: String?,
    )

    /** Paginated source of completed groups (10 per page). */
    fun completedGroupsPaged(): Flow<PagingData<PaluwaganGroup>>

    /** Hard-deletes completed groups whose `updated_at` is older than [cutoff]. */
    suspend fun purgeCompletedOlderThan(cutoff: Long)

    /** Admin hard-delete of a single completed group and its children. */
    suspend fun hardDeleteGroup(groupId: String)

    /**
     * Writes all payment rows for a single payment session — the main round and
     * any advance/catch-up seeds — inside one database transaction.
     * Each [PaymentUpsert] is either an insert (null existingPaymentId) or an
     * update of an existing UNPAID row (non-null existingPaymentId).
     */
    suspend fun recordPaymentAtomic(upserts: List<PaymentUpsert>)
}
