package com.ykfj.inventory.domain.usecase.goldpurchase

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.GoldPurchaseItem
import com.ykfj.inventory.domain.model.GoldPurchaseRecord
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

/**
 * Records a gold purchase (buying scrap/2nd-hand jewelry from a customer).
 *
 * All value computation happens here — no UI-computed totals are trusted.
 * Requires ≥ 1 item; each item must have weight > 0 and buyRatePerGram > 0.
 * The record and all items are inserted atomically.
 */
class AddGoldPurchaseUseCase @Inject constructor(
    private val goldPurchaseRepository: GoldPurchaseRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class ItemDraft(
        val description: String,
        val weightGrams: Double,
        val metalRateId: String? = null,
        val purity: String? = null,
        val buyRatePerGram: Double,
        val overrideValue: Double? = null,
        val photoFilename: String? = null,
    )

    data class Params(
        val customerId: String? = null,
        val items: List<ItemDraft>,
        val notes: String? = null,
        val recordedBy: String,
    )

    sealed class Result {
        data class Success(val recordId: String) : Result()
        object NoItems : Result()
        object InvalidItem : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        if (params.items.isEmpty()) return Result.NoItems

        val invalidItem = params.items.firstOrNull { it.weightGrams <= 0 || it.buyRatePerGram <= 0 }
        if (invalidItem != null) return Result.InvalidItem

        val now = System.currentTimeMillis()
        val recordId = UUID.randomUUID().toString()

        val items = params.items.map { draft ->
            val computed = draft.weightGrams * draft.buyRatePerGram
            val final = draft.overrideValue ?: computed
            GoldPurchaseItem(
                id = UUID.randomUUID().toString(),
                purchaseRecordId = recordId,
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

        val record = GoldPurchaseRecord(
            id = recordId,
            customerId = params.customerId,
            totalPaid = totalPaid,
            paidAt = now,
            notes = params.notes,
            recordedBy = params.recordedBy,
            linkedSoldRecordId = null,
            createdAt = now,
            updatedAt = now,
        )

        goldPurchaseRepository.insert(record, items)

        logActivity(
            userId = params.recordedBy,
            action = ActivityAction.GOLD_PURCHASED,
            description = "Gold purchase recorded — ${items.size} item(s), total ₱${"%.2f".format(totalPaid)}",
            entityType = "gold_purchase_record",
            entityId = recordId,
        )

        return Result.Success(recordId)
    }
}
