package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.LayawayTransaction
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import com.ykfj.inventory.util.CurrencyFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * Splits a single customer payment across multiple active layaways.
 * Each allocation creates one [LayawayTransaction]. Layaways that become
 * fully paid are auto-completed. Credit score is accumulated per allocation:
 * +10 on full on-time completion, +1 on partial on-time, −10 if past due date.
 */
class SplitLayawayPaymentUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Allocation(val layawayId: String, val amount: Double)

    data class Params(
        val allocations: List<Allocation>,
        val customerId: String,
        val actorUserId: String,
        val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    )

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result = try {
        val now = System.currentTimeMillis()
        var totalSplit = 0.0
        var creditDelta = 0

        for (allocation in params.allocations) {
            if (allocation.amount <= 0.0) continue
            val record = layawayRepository.getById(allocation.layawayId) ?: continue
            if (record.status != LayawayStatus.ACTIVE) continue

            layawayRepository.addPayment(
                LayawayTransaction(
                    id = UUID.randomUUID().toString(),
                    layawayId = allocation.layawayId,
                    amountPaid = allocation.amount,
                    paymentDate = now,
                    notes = "Split payment",
                    paymentMethod = params.paymentMethod,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            totalSplit += allocation.amount

            val isLate = record.dueDate != null && now > record.dueDate
            val isFullyPaid = record.totalPaid + allocation.amount >= record.unitPrice * record.quantity

            if (isFullyPaid) {
                layawayRepository.markCompleted(allocation.layawayId, now)
                val product = productRepository.getById(record.productId)
                if (product != null) {
                    val newStatus = if (product.quantity <= 0) ProductStatus.SOLD else ProductStatus.AVAILABLE
                    productRepository.setStatus(product.id, newStatus)
                }
            }

            creditDelta += when {
                isLate -> -10
                isFullyPaid -> 10
                else -> 1
            }
        }

        customerRepository.adjustCreditScore(params.customerId, creditDelta)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.PAYMENT,
            description = "Split payment ${CurrencyFormatter.format(totalSplit)} across ${params.allocations.size} layaways",
            entityType = "layaway_record",
        )
        Result.Success
    } catch (e: Exception) {
        Result.Error(e.message ?: "Split payment failed")
    }
}
