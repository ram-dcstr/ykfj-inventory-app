package com.ykfj.inventory.domain.usecase.supplier

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.Supplier
import com.ykfj.inventory.domain.repository.SupplierRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

class AddSupplierUseCase @Inject constructor(
    private val supplierRepository: SupplierRepository,
    private val logActivity: LogActivityUseCase,
) {
    suspend operator fun invoke(
        name: String,
        representativeName: String?,
        mobile: String?,
        address: String?,
        notes: String?,
        actorUserId: String,
    ): Supplier {
        val now = System.currentTimeMillis()
        val supplier = Supplier(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            representativeName = representativeName?.trim()?.ifBlank { null },
            mobile = mobile?.trim()?.ifBlank { null },
            address = address?.trim()?.ifBlank { null },
            notes = notes?.trim()?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
        )
        supplierRepository.upsert(supplier)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.CREATE,
            description = "Added supplier '${supplier.name}'",
            entityType = "supplier",
            entityId = supplier.id,
        )
        return supplier
    }
}
