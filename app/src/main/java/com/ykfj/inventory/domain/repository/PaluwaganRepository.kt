package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.PaluwaganGroup
import com.ykfj.inventory.domain.model.PaluwaganPayment
import com.ykfj.inventory.domain.model.PaluwaganSlot
import kotlinx.coroutines.flow.Flow

/**
 * Owns all three paluwagan entities (group, slot, payment). They are
 * tightly coupled — advancing a round inserts payments for every slot;
 * swapping positions mutates two slots atomically — so sharing a
 * repository keeps those multi-row invariants in one place.
 */
interface PaluwaganRepository {

    fun observeActiveGroups(): Flow<List<PaluwaganGroup>>

    fun observeGroup(groupId: String): Flow<PaluwaganGroup?>

    fun observeSlots(groupId: String): Flow<List<PaluwaganSlot>>

    fun observePayments(groupId: String): Flow<List<PaluwaganPayment>>

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
}
