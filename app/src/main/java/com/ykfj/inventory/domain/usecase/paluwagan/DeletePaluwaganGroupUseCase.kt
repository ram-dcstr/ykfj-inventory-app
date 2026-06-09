package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/** Admin only — enforce role before calling. */
class DeletePaluwaganGroupUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val groupId: String,
        val actorUserId: String,
    )

    suspend operator fun invoke(params: Params) {
        paluwaganRepository.deleteGroup(params.groupId)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.DELETE,
            description = "Deleted paluwagan group",
            entityType = "paluwagan_group",
            entityId = params.groupId,
        )
    }
}
