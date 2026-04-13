package com.ykfj.inventory.domain.usecase.activitylog

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.ActivityLog
import com.ykfj.inventory.domain.repository.ActivityLogRepository
import java.util.UUID
import javax.inject.Inject

/**
 * Writes a single audit log entry. Every mutating use case in Phases 2+
 * calls this on success so the activity log screen (Phase 6.5) has a
 * complete history.
 *
 * [oldValue] / [newValue] should be JSON strings on UPDATE actions and
 * `null` for everything else — the log screen diffs them when an entry
 * is expanded.
 */
class LogActivityUseCase @Inject constructor(
    private val activityLogRepository: ActivityLogRepository,
) {

    suspend operator fun invoke(
        userId: String,
        action: ActivityAction,
        description: String,
        entityType: String? = null,
        entityId: String? = null,
        oldValue: String? = null,
        newValue: String? = null,
    ) {
        activityLogRepository.insert(
            ActivityLog(
                id = UUID.randomUUID().toString(),
                userId = userId,
                action = action,
                entityType = entityType,
                entityId = entityId,
                description = description,
                oldValue = oldValue,
                newValue = newValue,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }
}
