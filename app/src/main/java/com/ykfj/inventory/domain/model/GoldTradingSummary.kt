package com.ykfj.inventory.domain.model

/**
 * Roll-up of gold-purchase items sold on to the shop's supplier within a time window.
 *
 * [profit] = [revenue] − sum of original buy prices.
 */
data class GoldTradingSummary(
    val itemsSold: Int,
    val revenue: Double,
    val profit: Double,
) {
    companion object {
        val Empty = GoldTradingSummary(0, 0.0, 0.0)
    }
}
