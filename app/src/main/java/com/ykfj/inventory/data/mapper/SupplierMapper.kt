package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.SupplierEntity
import com.ykfj.inventory.domain.model.Supplier

internal fun SupplierEntity.toDomain(): Supplier = Supplier(
    id = supplier_id,
    name = name,
    representativeName = representative_name,
    mobile = mobile,
    address = address,
    notes = notes,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun Supplier.toEntity(): SupplierEntity = SupplierEntity(
    supplier_id = id,
    name = name,
    representative_name = representativeName,
    mobile = mobile,
    address = address,
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
