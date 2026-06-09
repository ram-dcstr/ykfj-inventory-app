package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/** Admin / Manager only — enforce role before calling. */
class SwapPaluwaganPositionsUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val slotIdA: String,
        val slotIdB: String,
        val actorUserId: String,
    )

    suspend operator fun invoke(params: Params) {
        val a = paluwaganRepository.getSlotById(params.slotIdA) ?: return
        val b = paluwaganRepository.getSlotById(params.slotIdB) ?: return
        paluwaganRepository.swapPositions(params.slotIdA, params.slotIdB)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Swapped paluwagan positions ${a.position} ↔ ${b.position}",
            entityType = "paluwagan_slot",
            entityId = params.slotIdA,
        )
    }
}
