package com.ykfj.inventory.domain.usecase.goldpurchase

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Admin/Manager: soft-deletes a gold purchase record and all of its items.
 *
 * Trade-in records ([GoldPurchaseRecord.linkedSoldRecordId] ≠ null) cannot be
 * reverted independently — the full trade-in revert is handled atomically in
 * Phase 10 to keep both sides consistent.
 */
class RevertGoldPurchaseUseCase @Inject constructor(
    private val goldPurchaseRepository: GoldPurchaseRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val recordId: String,
        val reason: String,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        object NotFound : Result()
        object IsTradeIn : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val record = goldPurchaseRepository.getById(params.recordId)
            ?: return Result.NotFound
        if (record.linkedSoldRecordId != null) return Result.IsTradeIn
        return try {
            val now = System.currentTimeMillis()
            goldPurchaseRepository.softDeleteItems(params.recordId, now)
            goldPurchaseRepository.softDelete(params.recordId, now)

            logActivity(
                userId = params.actorUserId,
                action = ActivityAction.GOLD_PURCHASE_REVERTED,
                description = "Gold purchase ${params.recordId} reverted — reason: ${params.reason}",
                entityType = "gold_purchase_record",
                entityId = params.recordId,
            )
            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "Revert failed")
        }
    }
}
