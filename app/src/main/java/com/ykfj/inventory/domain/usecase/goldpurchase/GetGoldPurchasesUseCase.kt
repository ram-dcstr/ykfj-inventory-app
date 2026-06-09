package com.ykfj.inventory.domain.usecase.goldpurchase

import com.ykfj.inventory.domain.model.GoldPurchaseRecord
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Returns a live list of all non-deleted gold purchase records, newest first. */
class GetGoldPurchasesUseCase @Inject constructor(
    private val goldPurchaseRepository: GoldPurchaseRepository,
) {
    operator fun invoke(): Flow<List<GoldPurchaseRecord>> = goldPurchaseRepository.observeAll()
}
