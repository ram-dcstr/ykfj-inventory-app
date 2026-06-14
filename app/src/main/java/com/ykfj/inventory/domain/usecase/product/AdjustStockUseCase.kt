package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.StockAdjustmentReason
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.StockAdjustment
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.StockAdjustmentRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

/**
 * Removes [Params.quantity] units from a product's stock for a non-sale reason
 * (lost, stolen, miscount, returned to supplier, …) and records an auditable
 * [StockAdjustment]. This is the safe alternative to deleting a whole product
 * just to correct a count.
 *
 * Decrements `products.quantity`; if that brings the product to 0 units, the
 * product is soft-deleted so it leaves every inventory view (the write-off
 * record persists for the audit trail) — mirroring how a melted damaged item
 * removes its product.
 *
 * Admin only — stock write-offs are financially sensitive (each unit is gold).
 */
class AdjustStockUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val stockAdjustmentRepository: StockAdjustmentRepository,
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val productId: String,
        val actorUserId: String,
        val quantity: Int,
        val reason: StockAdjustmentReason,
        val notes: String?,
    )

    sealed class Result {
        object Success : Result()
        object ProductNotFound : Result()
        object NotAuthorized : Result()
        object InvalidQuantity : Result()
        /** Tried to remove more units than are in stock. */
        data class InsufficientStock(val available: Int) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val actor = userRepository.getById(params.actorUserId)
        if (actor == null || actor.role != UserRole.ADMIN) return Result.NotAuthorized

        val product = productRepository.getById(params.productId) ?: return Result.ProductNotFound
        if (params.quantity < 1) return Result.InvalidQuantity
        if (params.quantity > product.quantity) return Result.InsufficientStock(product.quantity)

        val now = System.currentTimeMillis()
        val record = StockAdjustment(
            id = UUID.randomUUID().toString(),
            productId = params.productId,
            quantity = params.quantity,
            reason = params.reason,
            notes = params.notes,
            recordedBy = params.actorUserId,
            dateRecorded = now,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        stockAdjustmentRepository.insert(record)
        productRepository.adjustQuantity(params.productId, -params.quantity)

        // No units left → remove the product from inventory entirely. The
        // adjustment record stays as the audit trail for what happened.
        if (product.quantity - params.quantity == 0) {
            productRepository.delete(params.productId)
        }

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Wrote off ${params.quantity} unit${if (params.quantity == 1) "" else "s"} " +
                "of '${product.name}' (${product.id}) — ${params.reason.label}",
            entityType = "stock_adjustment",
            entityId = record.id,
        )
        return Result.Success
    }
}
