package com.ykfj.inventory.domain.usecase.sold

import com.ykfj.inventory.domain.model.SoldRecord
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject

class GetSoldRecordsUseCase @Inject constructor(
    private val soldRecordRepository: SoldRecordRepository,
) {
    /**
     * Reactive list of non-deleted sold records for the day containing [dateMillis],
     * using the device's local time zone for midnight-to-midnight boundaries.
     */
    operator fun invoke(dateMillis: Long): Flow<List<SoldRecord>> {
        val start = startOfDay(dateMillis)
        val end = start + DAY_MILLIS - 1
        return soldRecordRepository.observeByDateRange(start, end)
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1_000L

        /** Returns the epoch-millis for midnight of the day that contains [epochMillis]. */
        fun startOfDay(epochMillis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = epochMillis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
    }
}
