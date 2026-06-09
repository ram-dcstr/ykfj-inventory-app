package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.PaymentMethod

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
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val createdAt: Long,
    val updatedAt: Long,
)
