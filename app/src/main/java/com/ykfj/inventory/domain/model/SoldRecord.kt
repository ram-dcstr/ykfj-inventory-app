package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.DiscountType

/**
 * Historical record of one sale. Prices are snapshotted at sale time so
 * reports remain accurate even when metal rates later change.
 *
 * [soldPrice] and [capitalPrice] are per-unit; row revenue is
 * `soldPrice × quantity` (already post-discount).
 */
data class SoldRecord(
    val id: String,
    val productId: String,
    val customerId: String?,
    val soldBy: String,
    val quantity: Int,
    val soldPrice: Double,
    val capitalPrice: Double,
    val discountAmount: Double,
    val discountType: DiscountType,
    val soldDate: Long,
    val notes: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
