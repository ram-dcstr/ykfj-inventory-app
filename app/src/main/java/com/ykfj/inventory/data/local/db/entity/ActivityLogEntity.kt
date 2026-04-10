package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ykfj.inventory.data.local.db.enums.ActivityAction

/**
 * Immutable audit log. Rows are hard-deleted by the nightly cleanup job
 * once older than 90 days — do **not** add `is_deleted` / `is_archived`.
 *
 * [old_value] / [new_value] hold JSON payloads for diffs on UPDATE actions.
 */
@Entity(
    tableName = "activity_logs",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["action"]),
        Index(value = ["entity_type"]),
        Index(value = ["entity_id"]),
        Index(value = ["timestamp"]),
    ],
)
data class ActivityLogEntity(
    @PrimaryKey val log_id: String,
    val user_id: String,
    val action: ActivityAction,
    val entity_type: String? = null,
    val entity_id: String? = null,
    val description: String,
    val old_value: String? = null,
    val new_value: String? = null,
    val timestamp: Long,
)
