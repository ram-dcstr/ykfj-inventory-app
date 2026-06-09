package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/** Admin / Manager only — enforce role before calling. */
class CompletePaluwaganGroupUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val groupId: String,
        val actorUserId: String,
    )

    suspend operator fun invoke(params: Params) {
        val group = paluwaganRepository.observeGroup(params.groupId).firstOrNull() ?: return
        paluwaganRepository.completeGroup(params.groupId)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Completed paluwagan group '${group.name}' (all ${group.totalSlots} rounds done)",
            entityType = "paluwagan_group",
            entityId = params.groupId,
        )
    }
}
