package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.repository.LayawayRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActiveLayawaysUseCase @Inject constructor(
    private val layawayRepository: LayawayRepository,
) {
    operator fun invoke(): Flow<List<LayawayRecord>> = layawayRepository.observeActive()
}
