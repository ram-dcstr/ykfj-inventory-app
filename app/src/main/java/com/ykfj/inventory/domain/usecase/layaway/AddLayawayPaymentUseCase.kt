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
 * Records a payment on a layaway. Credit score: +1 on-time partial, +10 on full on-time
 * completion, −10 if past due date.
 */
class AddLayawayPaymentUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val layawayId: String,
        val amount: Double,
        val notes: String?,
        val actorUserId: String,
        val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    )

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
        object AlreadyCompleted : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result { return try {
        val record = layawayRepository.getById(params.layawayId)
            ?: return Result.RecordNotFound
        if (record.status != LayawayStatus.ACTIVE) return Result.AlreadyCompleted

        val now = System.currentTimeMillis()
        val transaction = LayawayTransaction(
            id = UUID.randomUUID().toString(),
            layawayId = params.layawayId,
            amountPaid = params.amount,
            paymentDate = now,
            notes = params.notes,
            paymentMethod = params.paymentMethod,
            createdAt = now,
            updatedAt = now,
        )
        layawayRepository.addPayment(transaction)

        val isLate = record.dueDate != null && now > record.dueDate
        val isFullyPaid = record.totalPaid + params.amount >= record.unitPrice * record.quantity

        if (isFullyPaid) {
            layawayRepository.markCompleted(params.layawayId, now)
            val product = productRepository.getById(record.productId)
            if (product != null) {
                val newStatus = if (product.quantity <= 0) ProductStatus.SOLD else ProductStatus.AVAILABLE
                productRepository.setStatus(product.id, newStatus)
            }
        }

        val creditDelta = when {
            isLate -> -10
            isFullyPaid -> 10
            else -> 1
        }
        customerRepository.adjustCreditScore(record.customerId, creditDelta)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.PAYMENT,
            description = "Payment ${CurrencyFormatter.format(params.amount)} added to layaway ${params.layawayId}",
            entityType = "layaway_record",
            entityId = params.layawayId,
        )
        Result.Success
    } catch (e: Exception) {
        Result.Error(e.message ?: "Payment failed")
    } }
}
