package com.ykfj.inventory.domain.usecase.damaged

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Inverse of [MeltDamagedProductUseCase] — restores both the soft-deleted
 * product and the soft-deleted damaged record. The item reappears in the
 * Active damaged-list and the product is back in inventory (with whatever
 * status it carried prior to the melt).
 *
 * Admin only — enforce role at the call site.
 */
class RevertMeltUseCase @Inject constructor(
    private val damagedRecordRepository: DamagedRecordRepository,
    private val productRepository: ProductRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val damagedId: String,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val record = damagedRecordRepository.getById(params.damagedId)
            ?: return Result.RecordNotFound

        productRepository.restore(record.productId)
        damagedRecordRepository.restore(record.id)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.REVERT,
            description = "Reverted melt of product ${record.productId} (record + product restored).",
            entityType = "product",
            entityId = record.productId,
        )
        return Result.Success
    }
}
