package com.ykfj.inventory.domain.usecase.archive

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Marks a single completed record as archived. Admin / Manager only (enforced here).
 *
 * Archived records disappear from the default list views and only show up
 * in the per-screen "Archived" tab and in [ExportArchiveUseCase]. The
 * actual hard-delete step is opt-in via [PurgeArchivedRecordsUseCase].
 */
class ArchiveRecordUseCase @Inject constructor(
    private val soldRepo: SoldRecordRepository,
    private val layawayRepo: LayawayRepository,
    private val damagedRepo: DamagedRecordRepository,
    private val paluwaganRepo: PaluwaganRepository,
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data object Success : Result
        /** Actor's role is neither ADMIN nor MANAGER. */
        data object NotAuthorized : Result
    }

    suspend operator fun invoke(
        type: ArchivableRecordType,
        recordId: String,
        actorUserId: String,
    ): Result {
        val actor = userRepository.getById(actorUserId)
        if (actor == null || (actor.role != UserRole.ADMIN && actor.role != UserRole.MANAGER)) {
            return Result.NotAuthorized
        }

        when (type) {
            ArchivableRecordType.SOLD -> soldRepo.archive(recordId)
            ArchivableRecordType.LAYAWAY -> layawayRepo.archive(recordId)
            ArchivableRecordType.DAMAGED -> damagedRepo.archive(recordId)
            ArchivableRecordType.PALUWAGAN -> paluwaganRepo.archiveGroup(recordId)
        }
        logActivity(
            userId = actorUserId,
            action = ActivityAction.UPDATE,
            description = "Archived ${type.label} record",
            entityType = type.entityType,
            entityId = recordId,
        )
        return Result.Success
    }
}
