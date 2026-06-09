package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Admin-only: edit customer, quantity, unit price, and due date on an ACTIVE layaway.
 * Quantity changes are reflected in product inventory (delta applied via [adjustQuantity]).
 */
class UpdateLayawayUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val layawayId: String,
        val customerId: String,
        val quantity: Int,
        val unitPrice: Double,
        val dueDate: Long?,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
        object NotActive : Result()
        object InsufficientQuantity : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result { return try {
        val record = layawayRepository.getById(params.layawayId)
            ?: return Result.RecordNotFound
        if (record.status != LayawayStatus.ACTIVE) return Result.NotActive

        // Quantity change → adjust product inventory
        val qtyDelta = params.quantity - record.quantity
        if (qtyDelta > 0) {
            val product = productRepository.getById(record.productId)
                ?: return Result.RecordNotFound
            if (product.quantity < qtyDelta) return Result.InsufficientQuantity
        }
        if (qtyDelta != 0) {
            productRepository.adjustQuantity(record.productId, -qtyDelta)
        }

        layawayRepository.update(
            record.copy(
                customerId = params.customerId,
                quantity = params.quantity,
                unitPrice = params.unitPrice,
                dueDate = params.dueDate,
                updatedAt = System.currentTimeMillis(),
            ),
        )

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Updated layaway ${params.layawayId}",
            entityType = "layaway_record",
            entityId = params.layawayId,
        )
        Result.Success
    } catch (e: Exception) {
        Result.Error(e.message ?: "Update failed")
    } }
}
