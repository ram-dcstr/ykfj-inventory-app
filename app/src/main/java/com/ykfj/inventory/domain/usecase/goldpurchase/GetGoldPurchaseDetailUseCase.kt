package com.ykfj.inventory.domain.usecase.goldpurchase

import com.ykfj.inventory.domain.model.GoldPurchaseItem
import com.ykfj.inventory.domain.model.GoldPurchaseRecord
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** Combines the header record and its items into a single observable stream. */
class GetGoldPurchaseDetailUseCase @Inject constructor(
    private val goldPurchaseRepository: GoldPurchaseRepository,
) {
    operator fun invoke(recordId: String): Flow<Pair<GoldPurchaseRecord?, List<GoldPurchaseItem>>> =
        combine(
            goldPurchaseRepository.observeById(recordId),
            goldPurchaseRepository.observeItemsForRecord(recordId),
        ) { record, items -> record to items }
}
