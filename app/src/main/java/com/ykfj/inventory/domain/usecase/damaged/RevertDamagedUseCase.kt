package com.ykfj.inventory.domain.usecase.damaged

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Reverts a damaged record back to Available.
 *
 * - Soft-deletes the [DamagedRecord] (removes it from the active list).
 * - Restores the product quantity by +1 (which also flips status back to AVAILABLE).
 * - Logs the revert action.
 *
 * Admin / Manager only — enforce role before calling.
 */
class RevertDamagedUseCase @Inject constructor(
    private val damagedRecordRepository: DamagedRecordRepository,
    private val productRepository: ProductRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val damagedId: String,
        val reason: String,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val record = damagedRecordRepository.getById(params.damagedId)
            ?: return Result.RecordNotFound

        damagedRecordRepository.softDelete(record.id)
        productRepository.adjustQuantity(record.productId, delta = 1)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.REVERT,
            description = "Reverted damaged item ${record.productId}. Reason: ${params.reason}",
            entityType = "damaged_record",
            entityId = record.id,
        )
        return Result.Success
    }
}
