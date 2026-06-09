package com.ykfj.inventory.domain.usecase.activitylog

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.ActivityLog
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * Reads activity logs with the role-aware policy from CLAUDE.md:
 *
 *  - Admin / Manager: every log
 *  - Staff: only their own actions
 *
 * The role check happens in the use case so screens can't accidentally surface
 * other users' actions to staff. If [actor] is null (not logged in), an empty
 * stream is returned.
 */
class GetActivityLogsUseCase @Inject constructor(
    private val repository: ActivityLogRepository,
) {
    operator fun invoke(
        actor: User?,
        userIdFilter: String? = null,
        action: ActivityAction? = null,
        fromMillis: Long? = null,
        toMillis: Long? = null,
    ): Flow<List<ActivityLog>> {
        if (actor == null) return flowOf(emptyList())
        // Staff: lock the user filter to themselves regardless of what was requested.
        val effectiveUserId = if (actor.role == UserRole.STAFF) actor.id else userIdFilter
        return repository.observe(
            userId = effectiveUserId,
            action = action,
            entityType = null,
            fromEpochMillis = fromMillis,
            toEpochMillis = toMillis,
        )
    }
}
