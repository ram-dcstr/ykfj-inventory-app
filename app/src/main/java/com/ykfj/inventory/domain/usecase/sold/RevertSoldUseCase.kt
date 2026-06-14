package com.ykfj.inventory.domain.usecase.sold

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Reverts a specific sold record by [soldId].
 *
 * Supports partial revert: if [Params.quantity] < record.quantity, the record's
 * quantity is reduced and kept active. If it equals the full quantity, the record
 * is soft-deleted. In both cases the product quantity is restored by [Params.quantity].
 *
 * If the sold record originated from a completed layaway, the linked layaway record is
 * also cancelled — no quantity double-adjustment since the product restore above already
 * handles it. The link is read from [SoldRecord.linkedLayawayId]; rows created before
 * that column existed fall back to the legacy `notes = "layaway_complete:{id}"` marker.
 *
 * Admin / Manager only — enforce role before calling.
 */
class RevertSoldUseCase @Inject constructor(
    private val soldRecordRepository: SoldRecordRepository,
    private val productRepository: ProductRepository,
    private val layawayRepository: LayawayRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val soldId: String,
        /** How many units to revert (1..record.quantity). */
        val quantity: Int,
        val reason: String,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val record = soldRecordRepository.getById(params.soldId)
            ?: return Result.RecordNotFound

        val qty = params.quantity.coerceIn(1, record.quantity)
        val revertNote = buildRevertNote(params.reason, qty, record.notes)

        if (qty == record.quantity) {
            // Full revert — soft-delete the record
            soldRecordRepository.update(record.copy(notes = revertNote))
            soldRecordRepository.softDelete(record.id)
        } else {
            // Partial revert — reduce the record's quantity
            soldRecordRepository.update(
                record.copy(quantity = record.quantity - qty, notes = revertNote),
            )
        }

        productRepository.adjustQuantity(record.productId, qty)

        // If this sale came from a completed layaway, cancel that layaway record.
        // Quantity was already restored above — markCancelled only flips the status.
        // Prefer the dedicated column; fall back to the legacy notes marker for
        // rows created before linkedLayawayId existed.
        val layawayId = record.linkedLayawayId
            ?: record.notes?.takeIf { it.startsWith(LAYAWAY_COMPLETE_PREFIX) }
                ?.removePrefix(LAYAWAY_COMPLETE_PREFIX)
        if (!layawayId.isNullOrBlank()) {
            layawayRepository.markCancelled(layawayId, System.currentTimeMillis())
        }

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.REVERT,
            description = "Reverted ${qty}x ${record.productId} (of ${record.quantity} sold). Reason: ${params.reason}",
            entityType = "sold_record",
            entityId = record.id,
        )
        return Result.Success
    }

    private fun buildRevertNote(reason: String, qty: Int, existing: String?): String {
        val tag = "Reverted ${qty}x: $reason"
        return if (existing.isNullOrBlank()) tag else "$existing | $tag"
    }

    private companion object {
        const val LAYAWAY_COMPLETE_PREFIX = "layaway_complete:"
    }
}
