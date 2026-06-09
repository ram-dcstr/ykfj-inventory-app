package com.ykfj.inventory.domain.usecase.analytics

import com.ykfj.inventory.data.local.db.dao.AnalyticsDao
import com.ykfj.inventory.domain.model.CategorySalesEntry
import com.ykfj.inventory.domain.model.SalesSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetMonthlySalesUseCase @Inject constructor(
    private val analyticsDao: AnalyticsDao,
) {
    operator fun invoke(monthStartMillis: Long, monthEndMillis: Long): Flow<SalesSummary> =
        combine(
            analyticsDao.observeSalesMetrics(monthStartMillis, monthEndMillis),
            analyticsDao.observeTopCategories(monthStartMillis, monthEndMillis),
        ) { metrics, categories ->
            SalesSummary(
                revenue = metrics.revenue,
                capital = metrics.capital,
                profit = metrics.revenue - metrics.capital,
                itemCount = metrics.itemCount,
                topCategories = categories.map {
                    CategorySalesEntry(it.categoryId, it.categoryName, it.count)
                },
            )
        }
}
