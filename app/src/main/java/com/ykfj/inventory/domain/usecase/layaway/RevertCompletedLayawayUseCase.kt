package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Admin-only (enforced here): undoes [CompleteLayawayUseCase] for a COMPLETED record.
 *
 * Restores the layaway to ACTIVE, clears `completion_date`, soft-deletes the
 * auto-generated SoldRecord (so the Sold Archive and analytics stay accurate),
 * and flips the parent product status back to LAYAWAY.
 */
class RevertCompletedLayawayUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val soldRecordRepository: SoldRecordRepository,
    private val productRepository: ProductRepository,
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(val layawayId: String, val actorUserId: String)

    sealed class Result {
        object Success : Result()
        object RecordNotFound : Result()
        object NotCompleted : Result()
        /** Actor's role is not ADMIN. */
        object NotAuthorized : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val actor = userRepository.getById(params.actorUserId)
        if (actor == null || actor.role != UserRole.ADMIN) return Result.NotAuthorized

        val record = layawayRepository.getById(params.layawayId)
            ?: return Result.RecordNotFound
        if (record.status != LayawayStatus.COMPLETED) return Result.NotCompleted

        return try {
            layawayRepository.revertCompletion(params.layawayId)

            soldRecordRepository.findByLayawayCompletion(params.layawayId)
                ?.let { soldRecordRepository.softDelete(it.id) }

            productRepository.getById(record.productId)?.let { product ->
                productRepository.setStatus(product.id, ProductStatus.LAYAWAY)
            }

            logActivity(
                userId = params.actorUserId,
                action = ActivityAction.REVERT,
                description = "Reverted completed layaway ${params.layawayId} back to ACTIVE",
                entityType = "layaway_record",
                entityId = params.layawayId,
            )
            Result.Success
        } catch (e: Exception) {
            Result.Error(e.message ?: "Revert completion failed")
        }
    }
}
