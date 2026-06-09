package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import javax.inject.Inject

/**
 * Reverts the most recent sold or damaged record for a product:
 *  - Stamps the revert reason into the record's [notes] field for audit trail
 *  - Soft-deletes the record
 *  - Restores the quantity
 *  - Sets status back to AVAILABLE
 *
 * Admin / Manager only — callers must enforce role before invoking.
 */
class RevertStatusUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val soldRecordRepository: SoldRecordRepository,
    private val damagedRecordRepository: DamagedRecordRepository,
) {

    data class Params(
        val productId: String,
        val reason: String,
    )

    sealed class Result {
        object Success : Result()
        object ProductNotFound : Result()
        object NoRecordToRevert : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val product = productRepository.getById(params.productId) ?: return Result.ProductNotFound

        return when (product.status) {
            ProductStatus.SOLD -> {
                val record = soldRecordRepository.getMostRecentForProduct(params.productId)
                    ?: return Result.NoRecordToRevert
                val revertNote = buildRevertNote(params.reason, record.notes)
                soldRecordRepository.update(record.copy(notes = revertNote))
                soldRecordRepository.softDelete(record.id)
                productRepository.adjustQuantity(params.productId, record.quantity)
                Result.Success
            }
            ProductStatus.DAMAGED -> {
                val record = damagedRecordRepository.getMostRecentForProduct(params.productId)
                    ?: return Result.NoRecordToRevert
                val revertNote = buildRevertNote(params.reason, record.notes)
                damagedRecordRepository.update(record.copy(notes = revertNote))
                damagedRecordRepository.softDelete(record.id)
                productRepository.adjustQuantity(params.productId, 1)
                Result.Success
            }
            ProductStatus.LAYAWAY -> Result.NoRecordToRevert
            ProductStatus.AVAILABLE -> Result.NoRecordToRevert
        }
    }

    private fun buildRevertNote(reason: String, existingNotes: String?): String =
        if (existingNotes.isNullOrBlank()) "Reverted: $reason"
        else "$existingNotes | Reverted: $reason"
}
