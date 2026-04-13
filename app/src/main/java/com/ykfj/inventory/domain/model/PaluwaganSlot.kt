package com.ykfj.inventory.domain.model

/**
 * A slot in a paluwagan group. A customer may hold multiple slots in the
 * same group — each is a distinct row with its own [position] (1-based).
 */
data class PaluwaganSlot(
    val id: String,
    val groupId: String,
    val customerId: String,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
