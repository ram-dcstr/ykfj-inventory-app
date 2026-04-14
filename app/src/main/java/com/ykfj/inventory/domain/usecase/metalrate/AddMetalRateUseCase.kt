package com.ykfj.inventory.domain.usecase.metalrate

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.repository.MetalRateRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

class AddMetalRateUseCase @Inject constructor(
    private val metalRateRepository: MetalRateRepository,
    private val logActivity: LogActivityUseCase,
) {
    suspend operator fun invoke(
        name: String,
        pricePerGram: Double,
        actorUserId: String,
    ): MetalRate {
        val now = System.currentTimeMillis()
        val rate = MetalRate(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            pricePerGram = pricePerGram,
            createdAt = now,
            updatedAt = now,
        )
        metalRateRepository.upsert(rate)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.CREATE,
            description = "Added metal rate '${rate.name}' @ ₱${rate.pricePerGram}/g",
            entityType = "metal_rate",
            entityId = rate.id,
        )
        return rate
    }
}
