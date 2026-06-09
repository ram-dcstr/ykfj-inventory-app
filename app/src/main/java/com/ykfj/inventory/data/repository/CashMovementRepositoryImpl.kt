package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.CashMovementDao
import com.ykfj.inventory.data.local.db.enums.CashMovementType
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.CashMovement
import com.ykfj.inventory.domain.repository.CashMovementRepository
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class CashMovementRepositoryImpl @Inject constructor(
    private val cashMovementDao: CashMovementDao,
    private val syncEnqueuer: SyncEnqueuer,
) : CashMovementRepository {

    override fun observeForDay(dayStartMillis: Long): Flow<List<CashMovement>> =
        cashMovementDao.observeForDate(dayStartMillis)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getForTypeAndDay(
        type: CashMovementType,
        dayStartMillis: Long,
    ): CashMovement? = cashMovementDao.getForDate(dayStartMillis)
        .firstOrNull { it.type == type }
        ?.toDomain()

    override suspend fun upsert(movement: CashMovement) {
        val entity = movement.toEntity()
        val existing = cashMovementDao.getById(entity.id)
        if (existing == null) {
            cashMovementDao.insert(entity)
            syncEnqueuer.enqueueCashMovement(entity, SyncAction.INSERT)
        } else {
            cashMovementDao.update(entity)
            syncEnqueuer.enqueueCashMovement(entity, SyncAction.UPDATE)
        }
    }

    override suspend fun softDelete(id: String) {
        val existing = cashMovementDao.getById(id) ?: return
        val now = System.currentTimeMillis()
        cashMovementDao.softDelete(id, now)
        syncEnqueuer.enqueueCashMovement(
            existing.copy(is_deleted = true, updated_at = now),
            SyncAction.DELETE,
        )
    }
}
