package com.ykfj.inventory.domain.usecase.archive

import com.ykfj.inventory.data.local.db.dao.DamagedRecordDao
import com.ykfj.inventory.data.local.db.dao.LayawayRecordDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganGroupDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganPaymentDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganSlotDao
import com.ykfj.inventory.data.local.db.dao.SoldRecordDao
import com.ykfj.inventory.data.local.db.dao.UserDao
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Hard-deletes archived records of one [ArchivableRecordType] within a date
 * range. Admin-only (enforced here) — call this after a successful CSV export
 * so the data is preserved on disk before it leaves the database forever.
 *
 * For PALUWAGAN, child slots and payments are also hard-deleted to avoid
 * orphan rows; we look those up per-group rather than relying on a CASCADE
 * because the schema doesn't define one.
 */
class PurgeArchivedRecordsUseCase @Inject constructor(
    private val soldDao: SoldRecordDao,
    private val layawayDao: LayawayRecordDao,
    private val damagedDao: DamagedRecordDao,
    private val groupDao: PaluwaganGroupDao,
    private val slotDao: PaluwaganSlotDao,
    private val paymentDao: PaluwaganPaymentDao,
    private val userDao: UserDao,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        /** Number of rows hard-deleted (0 if none matched the range). */
        data class Success(val deleted: Int) : Result
        /** Actor's role is not ADMIN. */
        data object NotAuthorized : Result
    }

    suspend operator fun invoke(
        type: ArchivableRecordType,
        startMillis: Long,
        endMillis: Long,
        actorUserId: String,
    ): Result {
        val actor = userDao.getById(actorUserId)
        if (actor == null || actor.role != UserRole.ADMIN) return Result.NotAuthorized

        val deleted = when (type) {
            ArchivableRecordType.SOLD ->
                soldDao.hardDeleteArchivedInRange(startMillis, endMillis)
            ArchivableRecordType.LAYAWAY ->
                layawayDao.hardDeleteArchivedInRange(startMillis, endMillis)
            ArchivableRecordType.DAMAGED ->
                damagedDao.hardDeleteArchivedInRange(startMillis, endMillis)
            ArchivableRecordType.PALUWAGAN -> {
                // Collect group IDs first so we can clean up children, then delete groups.
                val groups = groupDao.getArchivedInRange(startMillis, endMillis)
                groups.forEach { g ->
                    paymentDao.hardDeleteByGroup(g.group_id)
                    slotDao.hardDeleteByGroup(g.group_id)
                }
                groupDao.hardDeleteArchivedInRange(startMillis, endMillis)
            }
        }

        if (deleted > 0) {
            logActivity(
                userId = actorUserId,
                action = ActivityAction.DELETE,
                description = "Purged $deleted archived ${type.label.lowercase()} record(s)",
                entityType = type.entityType,
            )
        }
        return Result.Success(deleted)
    }
}
