package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.DamagedRecord
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import java.util.UUID
import javax.inject.Inject

/** Always damages exactly 1 unit per call. */
class MarkAsDamagedUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val damagedRecordRepository: DamagedRecordRepository,
) {

    data class Params(
        val productId: String,
        val actorUserId: String,
        val reason: String,
        val notes: String? = null,
    )

    sealed class Result {
        data class Success(val damagedId: String) : Result()
        object ProductNotFound : Result()
        object NoUnitsAvailable : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val product = productRepository.getById(params.productId) ?: return Result.ProductNotFound
        if (product.quantity < 1) return Result.NoUnitsAvailable

        val now = System.currentTimeMillis()
        val record = DamagedRecord(
            id = UUID.randomUUID().toString(),
            productId = params.productId,
            recordedBy = params.actorUserId,
            reason = params.reason,
            dateRecorded = now,
            notes = params.notes,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        damagedRecordRepository.insert(record)
        productRepository.adjustQuantity(params.productId, -1)
        // If this was the last unit, mark status DAMAGED (not SOLD)
        val updated = productRepository.getById(params.productId)
        if (updated?.quantity == 0) {
            productRepository.setStatus(params.productId, ProductStatus.DAMAGED)
        }
        return Result.Success(record.id)
    }
}
