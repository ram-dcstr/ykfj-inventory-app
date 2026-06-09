package com.ykfj.inventory.domain.usecase.activitylog

import com.ykfj.inventory.domain.repository.ActivityLogRepository
import javax.inject.Inject

/**
 * Hard-deletes activity log rows older than [RETENTION_DAYS] days.
 * Wired into [com.ykfj.inventory.YkfjApp.onCreate] so it runs once per
 * launch — cheap, idempotent, and keeps the table bounded.
 */
class CleanupActivityLogsUseCase @Inject constructor(
    private val repository: ActivityLogRepository,
) {
    suspend operator fun invoke() {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * MS_PER_DAY
        repository.purgeOlderThan(cutoff)
    }

    private companion object {
        const val RETENTION_DAYS = 90L
        const val MS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
