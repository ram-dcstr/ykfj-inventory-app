package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/** Admin / Manager only — enforce role before calling. */
class ReorderPaluwaganSlotsUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val groupId: String,
        /** Slot IDs in the desired new order (position 1 first). */
        val orderedSlotIds: List<String>,
        val actorUserId: String,
    )

    suspend operator fun invoke(params: Params) {
        paluwaganRepository.reorderSlots(params.groupId, params.orderedSlotIds)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Reordered ${params.orderedSlotIds.size} paluwagan slots",
            entityType = "paluwagan_group",
            entityId = params.groupId,
        )
    }
}
