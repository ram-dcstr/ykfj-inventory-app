package com.ykfj.inventory.domain.usecase.analytics

import com.ykfj.inventory.data.local.db.dao.AnalyticsDao
import com.ykfj.inventory.domain.model.InventorySummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetInventorySummaryUseCase @Inject constructor(
    private val analyticsDao: AnalyticsDao,
) {
    operator fun invoke(): Flow<InventorySummary> =
        combine(
            analyticsDao.observeActiveProductCount(),
            analyticsDao.observeInventoryCapital(),
            analyticsDao.observeLayawayOutstanding(),
            analyticsDao.observeActivePaluwaganCount(),
        ) { count, capital, outstanding, paluwaganCount ->
            PartialSummary(count, capital, outstanding, paluwaganCount)
        }.combine(analyticsDao.observePaluwaganCollected()) { partial, collected ->
            InventorySummary(
                totalItems = partial.count,
                totalCapitalValue = partial.capital,
                layawayOutstanding = partial.outstanding,
                activePaluwaganGroups = partial.paluwaganCount,
                paluwaganTotalCollected = collected,
            )
        }

    private data class PartialSummary(
        val count: Int,
        val capital: Double,
        val outstanding: Double,
        val paluwaganCount: Int,
    )
}
