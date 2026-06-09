package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import java.util.UUID
import javax.inject.Inject

class MarkAsLayawayUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val layawayRepository: LayawayRepository,
) {

    data class Params(
        val productId: String,
        val actorUserId: String,
        val customerId: String,
        val quantity: Int,
        val unitPrice: Double,
        val dueDate: Long? = null,
    )

    sealed class Result {
        data class Success(val layawayId: String) : Result()
        object ProductNotFound : Result()
        object InsufficientQuantity : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val product = productRepository.getById(params.productId) ?: return Result.ProductNotFound
        if (params.quantity > product.quantity) return Result.InsufficientQuantity

        val now = System.currentTimeMillis()
        val record = LayawayRecord(
            id = UUID.randomUUID().toString(),
            productId = params.productId,
            customerId = params.customerId,
            createdBy = params.actorUserId,
            quantity = params.quantity,
            unitPrice = params.unitPrice,
            totalPaid = 0.0,
            dueDate = params.dueDate,
            status = LayawayStatus.ACTIVE,
            completionDate = null,
            forfeitedAmount = null,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        layawayRepository.insert(record)
        productRepository.adjustQuantity(params.productId, -params.quantity)
        // If all units are now on layaway, mark status LAYAWAY (not SOLD)
        val updated = productRepository.getById(params.productId)
        if (updated?.quantity == 0) {
            productRepository.setStatus(params.productId, ProductStatus.LAYAWAY)
        }
        return Result.Success(record.id)
    }
}
