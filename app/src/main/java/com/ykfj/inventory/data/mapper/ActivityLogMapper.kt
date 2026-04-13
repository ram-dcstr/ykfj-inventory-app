package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.ActivityLogEntity
import com.ykfj.inventory.domain.model.ActivityLog

internal fun ActivityLogEntity.toDomain(): ActivityLog = ActivityLog(
    id = log_id,
    userId = user_id,
    action = action,
    entityType = entity_type,
    entityId = entity_id,
    description = description,
    oldValue = old_value,
    newValue = new_value,
    timestamp = timestamp,
)

internal fun ActivityLog.toEntity(): ActivityLogEntity = ActivityLogEntity(
    log_id = id,
    user_id = userId,
    action = action,
    entity_type = entityType,
    entity_id = entityId,
    description = description,
    old_value = oldValue,
    new_value = newValue,
    timestamp = timestamp,
)
