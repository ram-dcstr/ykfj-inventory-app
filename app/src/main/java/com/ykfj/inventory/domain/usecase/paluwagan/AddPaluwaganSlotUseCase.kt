package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.PaluwaganSlot
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

/** Admin / Manager only — enforce role before calling. */
class AddPaluwaganSlotUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val groupId: String,
        val customerId: String,
        val position: Int,
        val actorUserId: String,
    )

    suspend operator fun invoke(params: Params) {
        val now = System.currentTimeMillis()
        val slot = PaluwaganSlot(
            id = UUID.randomUUID().toString(),
            groupId = params.groupId,
            customerId = params.customerId,
            position = params.position,
            createdAt = now,
            updatedAt = now,
        )
        paluwaganRepository.addSlot(slot)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.CREATE,
            description = "Added slot position ${params.position} to paluwagan group",
            entityType = "paluwagan_slot",
            entityId = slot.id,
        )
    }
}
