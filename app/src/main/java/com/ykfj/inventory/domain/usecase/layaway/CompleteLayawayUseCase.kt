package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.SoldRecord
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

/** Admin-only: manually mark a layaway as COMPLETED and record the sale in the Sold Archive. */
class CompleteLayawayUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val soldRecordRepository: SoldRecordRepository,
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
        layawayRepository.markCompleted(params.layawayId, now)

        // Update product status: quantity was already reduced at layaway creation.
        val product = productRepository.getById(record.productId)
        if (product != null) {
            val newStatus = if (product.quantity <= 0) ProductStatus.SOLD else ProductStatus.AVAILABLE
            productRepository.setStatus(product.id, newStatus)
        }

        // Create a SoldRecord so the completed layaway appears in the Sold Archive.
        val soldRecord = SoldRecord(
            id = UUID.randomUUID().toString(),
            productId = record.productId,
            customerId = record.customerId,
            soldBy = params.actorUserId,
            quantity = record.quantity,
            soldPrice = record.unitPrice,
            capitalPrice = product?.capitalPrice ?: 0.0,
            discountAmount = 0.0,
            discountType = DiscountType.NONE,
            soldDate = now,
            // Keep the notes marker for backward-compat with rows created before
            // linked_layaway_id existed; new reverts read the column directly.
            notes = "layaway_complete:${params.layawayId}",
            linkedLayawayId = params.layawayId,
            isArchived = false,
            createdAt = now,
            updatedAt = now,
        )
        soldRecordRepository.insert(soldRecord)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Completed layaway ${params.layawayId}",
            entityType = "layaway_record",
            entityId = params.layawayId,
        )
        Result.Success
    } catch (e: Exception) {
        Result.Error(e.message ?: "Completion failed")
    } }
}
