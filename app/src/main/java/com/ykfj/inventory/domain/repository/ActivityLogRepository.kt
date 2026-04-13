package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.ActivityLog
import kotlinx.coroutines.flow.Flow

/**
 * Write-heavy audit log. Every mutating use case is expected to call
 * `LogActivityUseCase` on success, which delegates here.
 *
 * Rows older than 90 days are hard-deleted by the nightly cleanup.
 */
interface ActivityLogRepository {

    /**
     * Observe logs filtered by the supplied criteria. Any null argument is
     * treated as "don't filter on this column".
     */
    fun observe(
        userId: String? = null,
        action: ActivityAction? = null,
        entityType: String? = null,
        fromEpochMillis: Long? = null,
        toEpochMillis: Long? = null,
    ): Flow<List<ActivityLog>>

    suspend fun insert(entry: ActivityLog)

    /** Hard-delete log entries older than [olderThanEpochMillis]. */
    suspend fun purgeOlderThan(olderThanEpochMillis: Long)
}
