package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import com.ykfj.inventory.util.CurrencyFormatter
import javax.inject.Inject

/**
 * Admin-only: cancel a layaway. Forfeits all paid amounts (no refund),
 * restores the reserved units back to available inventory, and applies
 * a -20 credit score penalty to the customer.
 *
 * Role is enforced here in the use case (not just at the UI), so any
 * caller — sync, future script, a misconfigured screen — is blocked the
 * same way a Staff user would be.
 */
class CancelLayawayUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(val layawayId: String, val actorUserId: String)

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
        object NotActive : Result()
        /** Actor's role is not ADMIN. */
        object NotAuthorized : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result { return try {
        val actor = userRepository.getById(params.actorUserId)
        if (actor == null || actor.role != UserRole.ADMIN) return Result.NotAuthorized

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
                "forfeited ${CurrencyFormatter.format(record.totalPaid)}",
            entityType = "layaway_record",
            entityId = params.layawayId,
        )
        Result.Success
    } catch (e: Exception) {
        Result.Error(e.message ?: "Cancellation failed")
    } }
}
