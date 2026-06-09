package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

/** Admin / Manager only — enforce role before calling. */
class AdvancePaluwaganRoundUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
    private val completePaluwaganGroup: CompletePaluwaganGroupUseCase,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val groupId: String,
        val actorUserId: String,
    )

    sealed class Result {
        data class Advanced(val newRound: Int) : Result()
        object Completed : Result()
        object GroupNotFound : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val group = paluwaganRepository.observeGroup(params.groupId).firstOrNull()
            ?: return Result.GroupNotFound

        if (group.currentRound >= group.totalSlots) {
            completePaluwaganGroup(CompletePaluwaganGroupUseCase.Params(params.groupId, params.actorUserId))
            return Result.Completed
        }

        paluwaganRepository.advanceRound(params.groupId)
        val newRound = group.currentRound + 1

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Advanced paluwagan '${group.name}' to round $newRound of ${group.totalSlots}",
            entityType = "paluwagan_group",
            entityId = params.groupId,
        )
        return Result.Advanced(newRound)
    }
}
