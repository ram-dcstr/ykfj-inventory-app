package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.ActivityLogDao
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.domain.model.ActivityLog
import com.ykfj.inventory.domain.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ActivityLogRepositoryImpl @Inject constructor(
    private val activityLogDao: ActivityLogDao,
) : ActivityLogRepository {

    override fun observe(
        userId: String?,
        action: ActivityAction?,
        entityType: String?,
        fromEpochMillis: Long?,
        toEpochMillis: Long?,
    ): Flow<List<ActivityLog>> =
        activityLogDao.observeFiltered(userId, action, entityType, fromEpochMillis, toEpochMillis)
            .map { list -> list.map { it.toDomain() } }

    override suspend fun insert(entry: ActivityLog) {
        activityLogDao.insert(entry.toEntity())
    }

    override suspend fun purgeOlderThan(olderThanEpochMillis: Long) {
        activityLogDao.deleteOlderThan(olderThanEpochMillis)
    }
}
