package com.ykfj.inventory.domain.model

/**
 * A slot in a paluwagan group. A customer may hold multiple slots in the
 * same group — each is a distinct row with its own [position] (1-based).
 */
data class PaluwaganSlot(
    val id: String,
    val groupId: String,
    val customerId: String,
    /** Set once on first pasalo — the customer who originally held this slot. Null if never transferred. */
    val originalCustomerId: String? = null,
    val position: Int,
    /** Actual date the collector physically received the pot. Null until recorded by admin. */
    val potCollectedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
