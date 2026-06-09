package com.ykfj.inventory.domain.usecase.supplier

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.SupplierRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

class DeleteSupplierUseCase @Inject constructor(
    private val supplierRepository: SupplierRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data class Blocked(val activeProductCount: Int) : Result
    }

    suspend operator fun invoke(id: String, actorUserId: String): Result {
        val existing = supplierRepository.getById(id) ?: return Result.NotFound

        val activeProducts = supplierRepository.countActiveProducts(id)
        if (activeProducts > 0) return Result.Blocked(activeProducts)

        supplierRepository.delete(id)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.DELETE,
            description = "Deleted supplier '${existing.name}'",
            entityType = "supplier",
            entityId = id,
        )
        return Result.Success
    }
}
