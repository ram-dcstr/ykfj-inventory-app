package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.LayawayStatus

/**
 * A layaway reservation. Only one ACTIVE layaway per product is permitted
 * (enforced in business logic, not DB constraint).
 *
 * Total owed = [unitPrice] × [quantity]. Payments accumulate into
 * [totalPaid] via [LayawayTransaction] rows. On CANCELLED,
 * [forfeitedAmount] is set to [totalPaid] (no refunds — forfeited payments
 * become shop profit).
 */
data class LayawayRecord(
    val id: String,
    val productId: String,
    val customerId: String,
    val createdBy: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPaid: Double,
    val dueDate: Long?,
    val status: LayawayStatus,
    val completionDate: Long?,
    val forfeitedAmount: Double?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
