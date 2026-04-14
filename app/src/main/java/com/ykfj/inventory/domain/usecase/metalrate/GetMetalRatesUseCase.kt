package com.ykfj.inventory.domain.usecase.metalrate

import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.repository.MetalRateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMetalRatesUseCase @Inject constructor(
    private val metalRateRepository: MetalRateRepository,
) {
    operator fun invoke(): Flow<List<MetalRate>> = metalRateRepository.observeAll()
}
