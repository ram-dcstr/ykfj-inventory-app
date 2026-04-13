package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.CustomerEntity
import com.ykfj.inventory.domain.model.Customer

internal fun CustomerEntity.toDomain(): Customer = Customer(
    id = customer_id,
    name = name,
    mobile = mobile,
    phone = phone,
    birthday = birthday,
    address = address,
    creditScore = credit_score,
    notes = notes,
    createdAt = created_at,
    updatedAt = updated_at,
)

internal fun Customer.toEntity(): CustomerEntity = CustomerEntity(
    customer_id = id,
    name = name,
    mobile = mobile,
    phone = phone,
    birthday = birthday,
    address = address,
    credit_score = creditScore,
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = false,
)
