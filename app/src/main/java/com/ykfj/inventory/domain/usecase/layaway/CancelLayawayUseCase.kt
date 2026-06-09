package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Admin-only: cancel a layaway. Forfeits all paid amounts (no refund),
 * restores the reserved units back to available inventory, and applies
 * a -20 credit score penalty to the customer.
 */
class CancelLayawayUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(val layawayId: String, val actorUserId: String)

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
        object NotActive : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result { return try {
        val record = layawayRepository.getById(params.layawayId)
            ?: return Result.RecordNotFound
        if (record.status != LayawayStatus.ACTIVE) return Result.NotActive

        val now = System.currentTimeMillis()
        layawayRepository.markCancelled(params.layawayId, now)

        // Restore reserved units back to product inventory; adjustQuantity auto-flips status
        productRepository.adjustQuantity(record.productId, record.quantity)

        // Credit penalty for cancelling a layaway contract
        customerRepository.adjustCreditScore(record.customerId, -20)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Cancelled layaway ${params.layawayId} — " +
                "forfeited ₱${"%.2f".format(record.totalPaid)}",
            entityType = "layaway_record",
            entityId = params.layawayId,
        )
        Result.Success
    } catch (e: Exception) {
        Result.Error(e.message ?: "Cancellation failed")
    } }
}
