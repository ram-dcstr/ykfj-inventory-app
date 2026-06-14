package com.ykfj.inventory.domain.usecase.goldpurchase

import androidx.room.withTransaction
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.GoldPurchaseItem
import com.ykfj.inventory.domain.model.GoldPurchaseRecord
import com.ykfj.inventory.domain.model.SoldRecord
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import com.ykfj.inventory.domain.usecase.common.DiscountCap
import com.ykfj.inventory.util.CurrencyFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * Atomic checkout that combines a sale and an inbound gold purchase ("trade-in").
 *
 * The customer hands over scrap or 2nd-hand jewelry as full or partial payment toward
 * the item they're buying. The shop's net cash position can be positive (customer pays
 * difference), zero (even swap), or negative (shop pays out difference) — all are valid.
 *
 * One transaction does:
 *  1. Insert the [SoldRecord] for the item being sold.
 *  2. Insert the [GoldPurchaseRecord] with `linkedSoldRecordId` pointing at (1), plus
 *     its [GoldPurchaseItem]s.
 *  3. Decrement product quantity by the sold quantity.
 *
 * The linked id is what lets [RevertTradeInUseCase] unwind both sides as one operation.
 *
 * Trade-in pricing and validation match [AddGoldPurchaseUseCase] — at least one item,
 * each with weight > 0 and rate > 0. No UI-computed totals are trusted.
 */
class SellWithTradeInUseCase @Inject constructor(
    private val db: YkfjDatabase,
    private val soldRecordRepository: SoldRecordRepository,
    private val goldPurchaseRepository: GoldPurchaseRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val productId: String,
        val actorUserId: String,
        // Sale side
        val quantity: Int,
        val soldPrice: Double,
        val capitalPrice: Double,
        val customerId: String? = null,
        val discountAmount: Double = 0.0,
        val discountType: DiscountType = DiscountType.NONE,
        val paymentMethod: PaymentMethod = PaymentMethod.CASH,
        val saleNotes: String? = null,
        // Trade-in side
        val tradeInItems: List<AddGoldPurchaseUseCase.ItemDraft>,
        val tradeInNotes: String? = null,
    )

    sealed class Result {
        data class Success(val soldId: String, val purchaseRecordId: String) : Result()
        object ProductNotFound : Result()
        object InsufficientQuantity : Result()
        object NoItems : Result()
        object InvalidItem : Result()
        /** Staff tried to apply a non-zero discount. Admin/Manager only. */
        object DiscountNotAuthorized : Result()
        /** Discount exceeds 20% of profit. */
        data class DiscountExceedsCap(val maxAllowed: Double) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        if (params.tradeInItems.isEmpty()) return Result.NoItems
        if (params.tradeInItems.any { it.weightGrams <= 0 || it.buyRatePerGram <= 0 }) return Result.InvalidItem

        val product = productRepository.getById(params.productId) ?: return Result.ProductNotFound
        if (params.quantity > product.quantity) return Result.InsufficientQuantity

        // Same discount-cap rule as MarkAsSoldUseCase. Trade-in is just a sale
        // paid partly in scrap — the discount must still be ≤ 20% of profit,
        // and Staff still can't discount.
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
        val soldId = UUID.randomUUID().toString()
        val purchaseId = UUID.randomUUID().toString()

        val items = params.tradeInItems.map { draft ->
            val computed = draft.weightGrams * draft.buyRatePerGram
            val final = draft.overrideValue ?: computed
            GoldPurchaseItem(
                id = UUID.randomUUID().toString(),
                purchaseRecordId = purchaseId,
                description = draft.description,
                weightGrams = draft.weightGrams,
                metalRateId = draft.metalRateId,
                purity = draft.purity,
                buyRatePerGram = draft.buyRatePerGram,
                computedValue = computed,
                overrideValue = draft.overrideValue,
                finalValue = final,
                photoFilename = draft.photoFilename,
                createdAt = now,
                updatedAt = now,
            )
        }
        val totalPaid = items.sumOf { it.finalValue }

        val sold = SoldRecord(
            id = soldId,
            productId = params.productId,
            customerId = params.customerId,
            soldBy = params.actorUserId,
            quantity = params.quantity,
            soldPrice = params.soldPrice,
            capitalPrice = params.capitalPrice,
            discountAmount = params.discountAmount,
            discountType = params.discountType,
            soldDate = now,
            notes = params.saleNotes,
            paymentMethod = params.paymentMethod,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )

        val purchase = GoldPurchaseRecord(
            id = purchaseId,
            customerId = params.customerId,
            totalPaid = totalPaid,
            paidAt = now,
            notes = params.tradeInNotes,
            recordedBy = params.actorUserId,
            linkedSoldRecordId = soldId,
            createdAt = now,
            updatedAt = now,
        )

        db.withTransaction {
            soldRecordRepository.insert(sold)
            goldPurchaseRepository.insert(purchase, items)
            productRepository.adjustQuantity(params.productId, -params.quantity)
        }

        val saleAmount = params.soldPrice * params.quantity
        val net = saleAmount - totalPaid
        val netDescription = when {
            net > 0 -> "customer paid ${CurrencyFormatter.format(net)} difference"
            net < 0 -> "shop paid ${CurrencyFormatter.format(-net)} difference"
            else -> "even swap"
        }
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.SELL,
            description = "Trade-in sale: ${params.quantity}x ${params.productId} for ${CurrencyFormatter.format(saleAmount)} against ${items.size} scrap item(s) worth ${CurrencyFormatter.format(totalPaid)} ($netDescription)",
            entityType = "sold_record",
            entityId = soldId,
        )
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.GOLD_PURCHASED,
            description = "Trade-in purchase recorded — ${items.size} item(s), total ${CurrencyFormatter.format(totalPaid)} (linked to sale $soldId)",
            entityType = "gold_purchase_record",
            entityId = purchaseId,
        )

        return Result.Success(soldId, purchaseId)
    }
}
