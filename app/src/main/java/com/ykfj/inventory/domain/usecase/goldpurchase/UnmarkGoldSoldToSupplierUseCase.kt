package com.ykfj.inventory.domain.usecase.goldpurchase

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/** Reverts a [MarkGoldSoldToSupplierUseCase] — clears the supplier sale fields. */
class UnmarkGoldSoldToSupplierUseCase @Inject constructor(
    private val goldPurchaseRepository: GoldPurchaseRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(val itemId: String, val actorUserId: String)

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result = try {
        val now = System.currentTimeMillis()
        goldPurchaseRepository.unmarkItemSoldToSupplier(params.itemId, now)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.GOLD_SOLD_TO_SUPPLIER_REVERTED,
            description = "Gold purchase item ${params.itemId} returned to in-stock",
            entityType = "gold_purchase_item",
            entityId = params.itemId,
        )
        Result.Success
    } catch (e: Exception) {
        Result.Error(e.message ?: "Failed to undo sale")
    }
}
