package com.ykfj.inventory.domain.usecase.customer

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

class AddCustomerUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
    private val logActivity: LogActivityUseCase,
) {
    suspend operator fun invoke(
        name: String,
        mobile: String?,
        phone: String?,
        birthday: Long?,
        address: String?,
        notes: String?,
        actorUserId: String,
    ): Customer {
        val now = System.currentTimeMillis()
        val customer = Customer(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            mobile = mobile?.trim()?.ifBlank { null },
            phone = phone?.trim()?.ifBlank { null },
            birthday = birthday,
            address = address?.trim()?.ifBlank { null },
            creditScore = 100,
            notes = notes?.trim()?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
        )
        customerRepository.upsert(customer)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.CREATE,
            description = "Added customer '${customer.name}'",
            entityType = "customer",
            entityId = customer.id,
        )
        return customer
    }
}
