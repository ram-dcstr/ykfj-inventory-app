package com.ykfj.inventory.domain.usecase.damaged

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Melts a damaged item: soft-deletes the parent product (the piece is gone for good)
 * and also soft-deletes the damaged record so it stops appearing in the Damaged list.
 *
 * Admin / Manager only — enforce role at the call site.
 */
class MeltDamagedProductUseCase @Inject constructor(
    private val damagedRecordRepository: DamagedRecordRepository,
    private val productRepository: ProductRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val damagedId: String,
        val notes: String?,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val record = damagedRecordRepository.getById(params.damagedId)
            ?: return Result.RecordNotFound

        productRepository.delete(record.productId)
        damagedRecordRepository.softDelete(record.id)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.DELETE,
            description = "Melted product ${record.productId} (damaged unit consumed; product removed).${params.notes?.let { " Notes: $it" }.orEmpty()}",
            entityType = "product",
            entityId = record.productId,
        )
        return Result.Success
    }
}
