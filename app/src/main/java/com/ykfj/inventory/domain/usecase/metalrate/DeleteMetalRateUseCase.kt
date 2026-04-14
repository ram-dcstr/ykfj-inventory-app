package com.ykfj.inventory.domain.usecase.metalrate

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.MetalRateRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

class DeleteMetalRateUseCase @Inject constructor(
    private val metalRateRepository: MetalRateRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data class Blocked(val activeProductCount: Int) : Result
    }

    suspend operator fun invoke(id: String, actorUserId: String): Result {
        val existing = metalRateRepository.getById(id) ?: return Result.NotFound

        val activeProducts = metalRateRepository.countActiveProducts(id)
        if (activeProducts > 0) return Result.Blocked(activeProducts)

        metalRateRepository.delete(id)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.DELETE,
            description = "Deleted metal rate '${existing.name}'",
            entityType = "metal_rate",
            entityId = id,
        )
        return Result.Success
    }
}
