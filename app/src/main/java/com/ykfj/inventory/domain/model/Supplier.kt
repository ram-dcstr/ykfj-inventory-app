package com.ykfj.inventory.domain.model

data class Supplier(
    val id: String,
    val name: String,
    val representativeName: String?,
    val mobile: String?,
    val address: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
