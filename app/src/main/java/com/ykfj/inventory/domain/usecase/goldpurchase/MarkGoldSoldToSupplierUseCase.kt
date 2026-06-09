package com.ykfj.inventory.domain.usecase.goldpurchase

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Records that a gold-purchase line item has been sold on to the shop's supplier.
 *
 * The supplier price is manually entered. Profit is computed at read time as
 * `soldToSupplierPrice − finalValue`, so this use case only stores the price
 * and the timestamp.
 */
class MarkGoldSoldToSupplierUseCase @Inject constructor(
    private val goldPurchaseRepository: GoldPurchaseRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val itemId: String,
        val supplierPrice: Double,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        object InvalidPrice : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        if (params.supplierPrice <= 0) return Result.InvalidPrice
        return try {
            val now = System.currentTimeMillis()
            goldPurchaseRepository.markItemSoldToSupplier(
                itemId = params.itemId,
                price = params.supplierPrice,
                soldAt = now,
                updatedAt = now,
            )
            logActivity(
                userId = params.actorUserId,
                action = ActivityAction.GOLD_SOLD_TO_SUPPLIER,
                description = "Gold purchase item ${params.itemId} sold to supplier for ₱${"%.2f".format(params.supplierPrice)}",
                entityType = "gold_purchase_item",
                entityId = params.itemId,
            )
            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to mark sold")
        }
    }
}
