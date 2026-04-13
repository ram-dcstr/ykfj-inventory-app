package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.ActivityAction

/**
 * Immutable audit log entry. Rows older than 90 days are hard-deleted by
 * the nightly cleanup — there is no soft-delete / archive flag.
 *
 * [oldValue] / [newValue] hold JSON payloads for diffs on UPDATE actions.
 */
data class ActivityLog(
    val id: String,
    val userId: String,
    val action: ActivityAction,
    val entityType: String?,
    val entityId: String?,
    val description: String,
    val oldValue: String?,
    val newValue: String?,
    val timestamp: Long,
)
