package com.ykfj.inventory.domain.model

/**
 * A named metal rate (e.g. "18K Saudi") with its current peso-per-gram price.
 * Updating [pricePerGram] instantly re-prices every WEIGHTED product using
 * this rate — weighted selling prices are computed at display time, never
 * stored.
 */
data class MetalRate(
    val id: String,
    val name: String,
    val pricePerGram: Double,
    val createdAt: Long,
    val updatedAt: Long,
)
