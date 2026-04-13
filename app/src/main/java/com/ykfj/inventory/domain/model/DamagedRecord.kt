package com.ykfj.inventory.domain.model

/**
 * A damaged-item record. Always 1 unit per row — if 3 units of a product
 * are damaged, there are 3 separate [DamagedRecord] instances.
 */
data class DamagedRecord(
    val id: String,
    val productId: String,
    val recordedBy: String,
    val reason: String,
    val dateRecorded: Long,
    val notes: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
