package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus

/**
 * Per-slot, per-round paluwagan payment row. The tuple
 * (`groupId`, `slotId`, `roundNumber`) is effectively unique.
 */
data class PaluwaganPayment(
    val id: String,
    val groupId: String,
    val slotId: String,
    val roundNumber: Int,
    val amountPaid: Double,
    val paymentDate: Long?,
    val status: PaluwaganPaymentStatus,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
