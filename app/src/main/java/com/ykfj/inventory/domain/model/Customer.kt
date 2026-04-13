package com.ykfj.inventory.domain.model

/**
 * Customer directory entry. Credit score starts at 100 and moves with
 * payment behavior: +1 on-time, −3 layaway late, −2 paluwagan late.
 */
data class Customer(
    val id: String,
    val name: String,
    val mobile: String?,
    val phone: String?,
    val birthday: Long?,
    val address: String?,
    val creditScore: Int,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
