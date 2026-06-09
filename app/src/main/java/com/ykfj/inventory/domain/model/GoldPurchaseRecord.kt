package com.ykfj.inventory.domain.model

/**
 * Header of a gold purchase transaction (buying scrap/2nd-hand jewelry from a customer).
 *
 * [linkedSoldRecordId] is null for straight purchases. Phase 10 sets it when
 * this purchase is the buy side of a trade-in — prevents one-sided revert.
 */
data class GoldPurchaseRecord(
    val id: String,
    val customerId: String?,
    val totalPaid: Double,
    val paidAt: Long,
    val notes: String?,
    val recordedBy: String,
    val linkedSoldRecordId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)
