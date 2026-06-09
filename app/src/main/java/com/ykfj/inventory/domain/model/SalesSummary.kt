package com.ykfj.inventory.domain.model

data class CategorySalesEntry(
    val categoryId: String,
    val categoryName: String,
    val count: Int,
)

data class SalesSummary(
    val revenue: Double,
    val capital: Double,
    val profit: Double,
    val itemCount: Int,
    val topCategories: List<CategorySalesEntry>,
) {
    companion object {
        val Empty = SalesSummary(0.0, 0.0, 0.0, 0, emptyList())
    }
}
