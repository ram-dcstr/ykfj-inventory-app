package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/** Admin-only: soft-delete a layaway payment transaction. Repository recalculates total_paid automatically. */
class DeleteLayawayPaymentUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val transactionId: String,
        val layawayId: String,
        val actorUserId: String,
    )

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result = try {
        layawayRepository.deletePayment(params.transactionId)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.DELETE,
            description = "Deleted payment ${params.transactionId} from layaway ${params.layawayId}",
            entityType = "layaway_transaction",
            entityId = params.transactionId,
        )
        Result.Success
    } catch (e: Exception) {
        Result.Error(e.message ?: "Delete failed")
    }
}
