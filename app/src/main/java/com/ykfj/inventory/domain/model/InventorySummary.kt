package com.ykfj.inventory.domain.model

data class InventorySummary(
    val totalItems: Int,
    val totalCapitalValue: Double,
    val layawayOutstanding: Double,
    val activePaluwaganGroups: Int,
    val paluwaganTotalCollected: Double,
) {
    companion object {
        val Empty = InventorySummary(0, 0.0, 0.0, 0, 0.0)
    }
}
