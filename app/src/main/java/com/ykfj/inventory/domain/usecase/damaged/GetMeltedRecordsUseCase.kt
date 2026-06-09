package com.ykfj.inventory.domain.usecase.damaged

import com.ykfj.inventory.domain.model.DamagedRecord
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMeltedRecordsUseCase @Inject constructor(
    private val damagedRecordRepository: DamagedRecordRepository,
) {
    operator fun invoke(): Flow<List<DamagedRecord>> = damagedRecordRepository.observeMelted()
}
