package com.ykfj.inventory.domain.model

/**
 * One payment line on a layaway. Split payments produce one
 * [LayawayTransaction] per target layaway with the allocated [amountPaid].
 */
data class LayawayTransaction(
    val id: String,
    val layawayId: String,
    val amountPaid: Double,
    val paymentDate: Long,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
