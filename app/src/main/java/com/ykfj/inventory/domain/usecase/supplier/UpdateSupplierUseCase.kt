package com.ykfj.inventory.domain.usecase.supplier

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.Supplier
import com.ykfj.inventory.domain.repository.SupplierRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

class UpdateSupplierUseCase @Inject constructor(
    private val supplierRepository: SupplierRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data class Success(val supplier: Supplier) : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(
        id: String,
        name: String,
        representativeName: String?,
        mobile: String?,
        address: String?,
        notes: String?,
        actorUserId: String,
    ): Result {
        val existing = supplierRepository.getById(id) ?: return Result.NotFound
        val updated = existing.copy(
            name = name.trim(),
            representativeName = representativeName?.trim()?.ifBlank { null },
            mobile = mobile?.trim()?.ifBlank { null },
            address = address?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            updatedAt = System.currentTimeMillis(),
        )
        supplierRepository.upsert(updated)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.UPDATE,
            description = "Updated supplier '${updated.name}'",
            entityType = "supplier",
            entityId = updated.id,
            oldValue = existing.name,
            newValue = updated.name,
        )
        return Result.Success(updated)
    }
}
