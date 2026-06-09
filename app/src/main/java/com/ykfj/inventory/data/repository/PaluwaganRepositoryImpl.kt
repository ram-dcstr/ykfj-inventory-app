package com.ykfj.inventory.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.dao.PaluwaganGroupDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganPaymentDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganSlotDao
import com.ykfj.inventory.data.local.db.entity.PaluwaganPaymentEntity
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.PaluwaganGroup
import com.ykfj.inventory.domain.model.PaluwaganPayment
import com.ykfj.inventory.domain.model.PaluwaganSlot
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.repository.PaymentUpsert
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class PaluwaganRepositoryImpl @Inject constructor(
    private val db: YkfjDatabase,
    private val groupDao: PaluwaganGroupDao,
    private val slotDao: PaluwaganSlotDao,
    private val paymentDao: PaluwaganPaymentDao,
    private val syncEnqueuer: SyncEnqueuer,
) : PaluwaganRepository {

    override fun observeActiveGroups(): Flow<List<PaluwaganGroup>> =
        groupDao.observeActive().map { it.map { e -> e.toDomain() } }

    override fun observeGroup(groupId: String): Flow<PaluwaganGroup?> =
        groupDao.observeById(groupId).map { it?.toDomain() }

    override fun observeSlots(groupId: String): Flow<List<PaluwaganSlot>> =
        slotDao.observeForGroup(groupId).map { it.map { e -> e.toDomain() } }

    override fun observePayments(groupId: String): Flow<List<PaluwaganPayment>> =
        paymentDao.observeForGroup(groupId).map { it.map { e -> e.toDomain() } }

    override fun observeSlotsForCustomer(customerId: String): Flow<List<PaluwaganSlot>> =
        slotDao.observeForCustomer(customerId).map { it.map { e -> e.toDomain() } }

    override fun observePaymentsForSlot(slotId: String): Flow<List<PaluwaganPayment>> =
        paymentDao.observeForSlot(slotId).map { it.map { e -> e.toDomain() } }

    override suspend fun getGroupById(groupId: String): PaluwaganGroup? =
        groupDao.getById(groupId)?.toDomain()

    override suspend fun createGroup(group: PaluwaganGroup) {
        val entity = group.toEntity()
        groupDao.insert(entity)
        syncEnqueuer.enqueuePaluwaganGroup(entity, SyncAction.INSERT)
    }

    override suspend fun updateGroup(group: PaluwaganGroup) {
        val entity = group.toEntity()
        groupDao.update(entity)
        syncEnqueuer.enqueuePaluwaganGroup(entity, SyncAction.UPDATE)
    }

    override suspend fun addSlot(slot: PaluwaganSlot) {
        val entity = slot.toEntity()
        slotDao.insert(entity)
        syncEnqueuer.enqueuePaluwaganSlot(entity, SyncAction.INSERT)
    }

    override suspend fun swapPositions(slotIdA: String, slotIdB: String) {
        val now = System.currentTimeMillis()
        val a = slotDao.getById(slotIdA) ?: return
        val b = slotDao.getById(slotIdB) ?: return
        slotDao.updatePosition(slotIdA, b.position, now)
        slotDao.updatePosition(slotIdB, a.position, now)
        syncEnqueuer.enqueuePaluwaganSlot(a.copy(position = b.position, updated_at = now))
        syncEnqueuer.enqueuePaluwaganSlot(b.copy(position = a.position, updated_at = now))
    }

    override suspend fun recordPayment(payment: PaluwaganPayment) {
        val entity = payment.toEntity()
        paymentDao.insert(entity)
        syncEnqueuer.enqueuePaluwaganPayment(entity, SyncAction.INSERT)
    }

    override suspend fun advanceRound(groupId: String) = db.withTransaction {
        val now = System.currentTimeMillis()
        val group = groupDao.getById(groupId) ?: return@withTransaction
        val nextRound = group.current_round + 1
        groupDao.updateCurrentRound(groupId, nextRound, now)
        syncEnqueuer.enqueuePaluwaganGroup(group.copy(current_round = nextRound, updated_at = now))

        val slots = slotDao.getSlotsForGroup(groupId)
        slots.forEach { slot ->
            val existing = paymentDao.getForSlotAndRound(slot.slot_id, nextRound)
            if (existing == null) {
                val newPayment = PaluwaganPaymentEntity(
                    payment_id = UUID.randomUUID().toString(),
                    group_id = groupId,
                    slot_id = slot.slot_id,
                    round_number = nextRound,
                    amount_paid = group.contribution_amount,
                    payment_date = null,
                    status = PaluwaganPaymentStatus.UNPAID,
                    notes = null,
                    created_at = now,
                    updated_at = now,
                    is_deleted = false,
                )
                paymentDao.insert(newPayment)
                syncEnqueuer.enqueuePaluwaganPayment(newPayment, SyncAction.INSERT)
            }
        }
    }

    override suspend fun completeGroup(groupId: String) {
        val group = groupDao.getById(groupId) ?: return
        val updated = group.copy(
            status = PaluwaganGroupStatus.COMPLETED,
            updated_at = System.currentTimeMillis(),
        )
        groupDao.update(updated)
        syncEnqueuer.enqueuePaluwaganGroup(updated)
    }

    override suspend fun archiveGroup(groupId: String) {
        val existing = groupDao.getById(groupId) ?: return
        val now = System.currentTimeMillis()
        groupDao.archive(groupId, now)
        syncEnqueuer.enqueuePaluwaganGroup(existing.copy(is_archived = true, updated_at = now))
    }

    override suspend fun deleteGroup(groupId: String) {
        val existing = groupDao.getById(groupId) ?: return
        val now = System.currentTimeMillis()
        groupDao.softDelete(groupId, now)
        syncEnqueuer.enqueuePaluwaganGroup(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }

    override suspend fun reorderSlots(groupId: String, orderedSlotIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedSlotIds.forEachIndexed { index, slotId ->
            val existing = slotDao.getById(slotId) ?: return@forEachIndexed
            val newPosition = index + 1
            slotDao.updatePosition(slotId, newPosition, now)
            syncEnqueuer.enqueuePaluwaganSlot(
                existing.copy(position = newPosition, updated_at = now),
            )
        }
    }

    override suspend fun updateSlotCustomer(slotId: String, newCustomerId: String) {
        val existing = slotDao.getById(slotId) ?: return
        val now = System.currentTimeMillis()
        slotDao.updateCustomer(slotId, newCustomerId, now)
        syncEnqueuer.enqueuePaluwaganSlot(
            existing.copy(customer_id = newCustomerId, updated_at = now),
        )
    }

    override suspend fun recordPotCollection(slotId: String, date: Long) {
        val existing = slotDao.getById(slotId) ?: return
        val now = System.currentTimeMillis()
        slotDao.updatePotCollectedAt(slotId, date, now)
        syncEnqueuer.enqueuePaluwaganSlot(
            existing.copy(pot_collected_at = date, updated_at = now),
        )
    }

    override suspend fun getSlotsForGroup(groupId: String): List<PaluwaganSlot> =
        slotDao.getSlotsForGroup(groupId).map { it.toDomain() }

    override suspend fun getSlotById(slotId: String): PaluwaganSlot? =
        slotDao.getById(slotId)?.toDomain()

    override suspend fun getPaymentForSlotRound(slotId: String, roundNumber: Int): PaluwaganPayment? =
        paymentDao.getForSlotAndRound(slotId, roundNumber)?.toDomain()

    override suspend fun updatePaymentStatus(
        paymentId: String,
        status: PaluwaganPaymentStatus,
        paymentDate: Long,
        amountPaid: Double,
        paymentMethod: PaymentMethod?,
        notes: String?,
    ) {
        paymentDao.updateStatus(
            paymentId = paymentId,
            status = status,
            paymentDate = paymentDate,
            amountPaid = amountPaid,
            paymentChannel = paymentMethod?.name,
            notes = notes,
            now = System.currentTimeMillis(),
        )
        paymentDao.getById(paymentId)?.let { syncEnqueuer.enqueuePaluwaganPayment(it) }
    }

    override suspend fun updatePaymentFull(
        paymentId: String,
        status: PaluwaganPaymentStatus,
        paymentDate: Long,
        amountPaid: Double,
        paymentMethod: PaymentMethod?,
        notes: String?,
    ) {
        paymentDao.updatePaymentFull(
            paymentId = paymentId,
            status = status,
            paymentDate = paymentDate,
            amountPaid = amountPaid,
            paymentChannel = paymentMethod?.name,
            notes = notes,
            now = System.currentTimeMillis(),
        )
        paymentDao.getById(paymentId)?.let { syncEnqueuer.enqueuePaluwaganPayment(it) }
    }

    override fun completedGroupsPaged(): Flow<PagingData<PaluwaganGroup>> =
        Pager(PagingConfig(pageSize = 10, enablePlaceholders = false)) {
            groupDao.completedGroupsPaged()
        }.flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override suspend fun purgeCompletedOlderThan(cutoff: Long) =
        groupDao.purgeCompletedOlderThan(cutoff)

    override suspend fun hardDeleteGroup(groupId: String) = db.withTransaction {
        paymentDao.hardDeleteByGroup(groupId)
        slotDao.hardDeleteByGroup(groupId)
        groupDao.hardDelete(groupId)
    }

    override suspend fun recordPaymentAtomic(upserts: List<PaymentUpsert>) = db.withTransaction {
        val now = System.currentTimeMillis()
        upserts.forEach { upsert ->
            val p = upsert.payment
            if (upsert.existingPaymentId == null) {
                val entity = p.toEntity()
                paymentDao.insert(entity)
                syncEnqueuer.enqueuePaluwaganPayment(entity, SyncAction.INSERT)
            } else {
                paymentDao.updateStatus(
                    paymentId = upsert.existingPaymentId,
                    status = p.status,
                    paymentDate = p.paymentDate ?: now,
                    amountPaid = p.amountPaid,
                    paymentChannel = p.paymentMethod?.name,
                    notes = p.notes,
                    now = now,
                )
                paymentDao.getById(upsert.existingPaymentId)?.let {
                    syncEnqueuer.enqueuePaluwaganPayment(it)
                }
            }
        }
    }
}
