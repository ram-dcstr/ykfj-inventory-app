package com.ykfj.inventory.domain.usecase.analytics

import com.ykfj.inventory.domain.model.GoldTradingSummary
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetGoldTradingSummaryUseCase @Inject constructor(
    private val goldPurchaseRepository: GoldPurchaseRepository,
) {
    operator fun invoke(startMillis: Long, endMillis: Long): Flow<GoldTradingSummary> =
        combine(
            goldPurchaseRepository.observeSupplierSoldCount(startMillis, endMillis),
            goldPurchaseRepository.observeSupplierRevenue(startMillis, endMillis),
            goldPurchaseRepository.observeSupplierProfit(startMillis, endMillis),
        ) { count, revenue, profit ->
            GoldTradingSummary(itemsSold = count, revenue = revenue, profit = profit)
        }
}
