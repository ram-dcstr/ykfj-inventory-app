package com.ykfj.inventory.domain.usecase.customer

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

class UpdateCustomerUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data class Success(val customer: Customer) : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(
        id: String,
        name: String,
        mobile: String?,
        phone: String?,
        birthday: Long?,
        address: String?,
        notes: String?,
        actorUserId: String,
    ): Result {
        val existing = customerRepository.getById(id) ?: return Result.NotFound
        val updated = existing.copy(
            name = name.trim(),
            mobile = mobile?.trim()?.ifBlank { null },
            phone = phone?.trim()?.ifBlank { null },
            birthday = birthday,
            address = address?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            updatedAt = System.currentTimeMillis(),
        )
        customerRepository.upsert(updated)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.UPDATE,
            description = "Updated customer '${updated.name}'",
            entityType = "customer",
            entityId = updated.id,
            oldValue = existing.name,
            newValue = updated.name,
        )
        return Result.Success(updated)
    }
}
