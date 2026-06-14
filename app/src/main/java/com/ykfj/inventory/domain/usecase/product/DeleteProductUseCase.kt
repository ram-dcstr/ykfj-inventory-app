package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Soft-deletes a product after enforcing the deletion guard from
 * [docs/business/Inventory-Rules.md]:
 *
 *   "Products: cannot delete if any sold/layaway/damaged records reference them
 *    — must revert first."
 *
 * The guard runs in this use case rather than in [ProductRepository.delete] so
 * the low-level repo method stays usable for intentional "delete with known
 * references" paths (e.g. [com.ykfj.inventory.domain.usecase.damaged.MeltDamagedProductUseCase]
 * deletes a product that's the target of the damaged record it's melting).
 *
 * Admin only — Staff and Manager don't see the delete affordance, but the
 * use case enforces it anyway.
 */
class DeleteProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val soldRecordRepository: SoldRecordRepository,
    private val layawayRepository: LayawayRepository,
    private val damagedRecordRepository: DamagedRecordRepository,
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(val productId: String, val actorUserId: String)

    sealed class Result {
        object Success : Result()
        object NotFound : Result()
        /** Actor is not ADMIN. */
        object NotAuthorized : Result()
        /**
         * Product still has active references. Counts are the non-deleted rows
         * currently pointing at it; the user must revert/archive those first.
         */
        data class HasReferences(
            val soldCount: Int,
            val layawayCount: Int,
            val damagedCount: Int,
        ) : Result() {
            val total: Int get() = soldCount + layawayCount + damagedCount
        }
    }

    suspend operator fun invoke(params: Params): Result {
        val actor = userRepository.getById(params.actorUserId)
        if (actor == null || actor.role != UserRole.ADMIN) return Result.NotAuthorized

        val product = productRepository.getById(params.productId) ?: return Result.NotFound

        val soldCount = soldRecordRepository.countActiveForProduct(params.productId)
        val layawayCount = layawayRepository.countActiveForProduct(params.productId)
        val damagedCount = damagedRecordRepository.countActiveForProduct(params.productId)
        if (soldCount + layawayCount + damagedCount > 0) {
            return Result.HasReferences(soldCount, layawayCount, damagedCount)
        }

        productRepository.delete(params.productId)
        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.DELETE,
            description = "Deleted product '${product.name}' (${product.id})",
            entityType = "product",
            entityId = product.id,
        )
        return Result.Success
    }
}
