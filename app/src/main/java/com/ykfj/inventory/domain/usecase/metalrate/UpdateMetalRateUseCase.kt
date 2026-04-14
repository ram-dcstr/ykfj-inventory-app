package com.ykfj.inventory.domain.usecase.metalrate

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.repository.MetalRateRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Updating a rate's [pricePerGram] instantly re-prices every WEIGHTED product
 * using it — weighted selling prices are always computed at display time
 * (`weight × rate`) and never stored.
 */
class UpdateMetalRateUseCase @Inject constructor(
    private val metalRateRepository: MetalRateRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data class Success(val rate: MetalRate) : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(
        id: String,
        name: String,
        pricePerGram: Double,
        actorUserId: String,
    ): Result {
        val existing = metalRateRepository.getById(id) ?: return Result.NotFound
        val updated = existing.copy(
            name = name.trim(),
            pricePerGram = pricePerGram,
            updatedAt = System.currentTimeMillis(),
        )
        metalRateRepository.upsert(updated)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.UPDATE,
            description = "Updated metal rate '${updated.name}' @ ₱${updated.pricePerGram}/g",
            entityType = "metal_rate",
            entityId = updated.id,
            oldValue = "${existing.name}|${existing.pricePerGram}",
            newValue = "${updated.name}|${updated.pricePerGram}",
        )
        return Result.Success(updated)
    }
}
