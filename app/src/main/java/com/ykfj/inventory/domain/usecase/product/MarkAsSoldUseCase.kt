package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.SoldRecord
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.common.DiscountCap
import java.util.UUID
import javax.inject.Inject

class MarkAsSoldUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val soldRecordRepository: SoldRecordRepository,
    private val userRepository: UserRepository,
) {

    data class Params(
        val productId: String,
        val actorUserId: String,
        val quantity: Int,
        /** Per-unit final sale price (after any discount). */
        val soldPrice: Double,
        /** Per-unit capital snapshot at time of sale. */
        val capitalPrice: Double,
        val customerId: String? = null,
        val discountAmount: Double = 0.0,
        val discountType: DiscountType = DiscountType.NONE,
        val paymentMethod: PaymentMethod = PaymentMethod.CASH,
        val notes: String? = null,
    )

    sealed class Result {
        data class Success(val soldId: String) : Result()
        object ProductNotFound : Result()
        object InsufficientQuantity : Result()
        /** Staff tried to apply a non-zero discount. Per Pricing-and-Discounts.md, Admin/Manager only. */
        object DiscountNotAuthorized : Result()
        /** Discount exceeds 20% of profit. [maxAllowed] is the per-unit cap that would have passed. */
        data class DiscountExceedsCap(val maxAllowed: Double) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val product = productRepository.getById(params.productId) ?: return Result.ProductNotFound
        if (params.quantity > product.quantity) return Result.InsufficientQuantity

        // Server-side discount-cap enforcement. The UI guard in SoldDialog isn't
        // enough — this use case is also reachable from trade-in flows and could
        // be called by sync/scripts in the future. Per Pricing-and-Discounts.md:
        // Staff cannot discount; discount ≤ 20% of profit for Admin/Manager.
        if (params.discountAmount > 0.0) {
            val actor = userRepository.getById(params.actorUserId)
            if (actor == null ||
                (actor.role != UserRole.ADMIN && actor.role != UserRole.MANAGER)
            ) {
                return Result.DiscountNotAuthorized
            }
            val maxDiscount = DiscountCap.maxPerUnit(params.soldPrice, params.capitalPrice)
            if (params.discountAmount > maxDiscount + DiscountCap.EPSILON) {
                return Result.DiscountExceedsCap(maxDiscount)
            }
        }

        val now = System.currentTimeMillis()
        val record = SoldRecord(
            id = UUID.randomUUID().toString(),
            productId = params.productId,
            customerId = params.customerId,
            soldBy = params.actorUserId,
            quantity = params.quantity,
            soldPrice = params.soldPrice,
            capitalPrice = params.capitalPrice,
            discountAmount = params.discountAmount,
            discountType = params.discountType,
            soldDate = now,
            notes = params.notes,
            paymentMethod = params.paymentMethod,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        soldRecordRepository.insert(record)
        productRepository.adjustQuantity(params.productId, -params.quantity)
        return Result.Success(record.id)
    }
}
