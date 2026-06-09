package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus
import com.ykfj.inventory.domain.model.PaluwaganGroup
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

/** Admin / Manager only — enforce role before calling. */
class CreatePaluwaganGroupUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val name: String,
        val contributionAmount: Double,
        val frequencyDays: Int,
        val totalSlots: Int,
        val startDate: Long,
        val notes: String?,
        val actorUserId: String,
    )

    suspend operator fun invoke(params: Params): PaluwaganGroup {
        val now = System.currentTimeMillis()
        val group = PaluwaganGroup(
            id = UUID.randomUUID().toString(),
            name = params.name,
            contributionAmount = params.contributionAmount,
            frequencyDays = params.frequencyDays,
            totalSlots = params.totalSlots,
            currentRound = 0,
            status = PaluwaganGroupStatus.ACTIVE,
            startDate = params.startDate,
            notes = params.notes,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        paluwaganRepository.createGroup(group)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.CREATE,
            description = "Created paluwagan group '${params.name}' " +
                "(${params.totalSlots} slots, ₱${params.contributionAmount} every ${params.frequencyDays} days)",
            entityType = "paluwagan_group",
            entityId = group.id,
        )
        return group
    }
}
