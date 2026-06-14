package com.ykfj.inventory.domain.usecase.goldpurchase

import androidx.room.withTransaction
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Admin/Manager: atomically undoes a trade-in.
 *
 * A trade-in is a gold purchase whose [com.ykfj.inventory.domain.model.GoldPurchaseRecord.linkedSoldRecordId]
 * points at the [com.ykfj.inventory.domain.model.SoldRecord] created in the same checkout
 * (see [SellWithTradeInUseCase]). Reverting it has to unwind both sides — leaving the sale
 * intact while removing the trade-in would mean the customer paid less than the sold record
 * shows, throwing off both inventory and analytics.
 *
 * Steps inside a single Room transaction:
 *  1. Soft-delete the gold purchase items.
 *  2. Soft-delete the gold purchase record.
 *  3. Soft-delete the sold record.
 *  4. Restore the product quantity by the sold quantity.
 *
 * Activity is logged with both [ActivityAction.GOLD_PURCHASE_REVERTED] and [ActivityAction.REVERT]
 * so the audit trail shows both sides being undone.
 *
 * Admin or Manager only — enforced in code here, not just at the UI.
 */
class RevertTradeInUseCase @Inject constructor(
    private val db: YkfjDatabase,
    private val goldPurchaseRepository: GoldPurchaseRepository,
    private val soldRecordRepository: SoldRecordRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val goldPurchaseRecordId: String,
        val reason: String,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        /** No gold purchase record with that id, or its `linkedSoldRecordId` is null, or the linked sold record doesn't exist. */
        object NotFound : Result()
        /** Actor's role is neither ADMIN nor MANAGER. */
        object NotAuthorized : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val actor = userRepository.getById(params.actorUserId)
        if (actor == null || (actor.role != UserRole.ADMIN && actor.role != UserRole.MANAGER)) {
            return Result.NotAuthorized
        }

        val purchase = goldPurchaseRepository.getById(params.goldPurchaseRecordId)
            ?: return Result.NotFound
        val soldId = purchase.linkedSoldRecordId ?: return Result.NotFound
        val sold = soldRecordRepository.getById(soldId) ?: return Result.NotFound

        return try {
            val now = System.currentTimeMillis()
            db.withTransaction {
                goldPurchaseRepository.softDeleteItems(purchase.id, now)
                goldPurchaseRepository.softDelete(purchase.id, now)
                soldRecordRepository.softDelete(sold.id)
                productRepository.adjustQuantity(sold.productId, sold.quantity)
            }

            logActivity(
                userId = params.actorUserId,
                action = ActivityAction.GOLD_PURCHASE_REVERTED,
                description = "Trade-in ${purchase.id} reverted (linked sale ${sold.id}). Reason: ${params.reason}",
                entityType = "gold_purchase_record",
                entityId = purchase.id,
            )
            logActivity(
                userId = params.actorUserId,
                action = ActivityAction.REVERT,
                description = "Reverted ${sold.quantity}x ${sold.productId} from trade-in ${purchase.id}. Reason: ${params.reason}",
                entityType = "sold_record",
                entityId = sold.id,
            )
            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "Trade-in revert failed")
        }
    }
}
