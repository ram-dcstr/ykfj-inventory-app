package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.StockAdjustmentReason

/**
 * A manual stock write-off: [quantity] units of [productId] removed from inventory
 * for [reason] (lost, stolen, miscount, returned to supplier, other).
 */
data class StockAdjustment(
    val id: String,
    val productId: String,
    val quantity: Int,
    val reason: StockAdjustmentReason,
    val notes: String?,
    val recordedBy: String,
    val dateRecorded: Long,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
