package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Phone-only offline change queue. Every local mutation (INSERT/UPDATE/DELETE)
 * is enqueued here when the tablet is unreachable; entries are drained by
 * `PendingSyncManager` when the connection comes back.
 *
 * The tablet's copy of this table stays empty — it is the source of truth
 * and has nothing to queue.
 */
@Entity(
    tableName = "pending_sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["entity_type"]),
        Index(value = ["entity_id"]),
    ],
)
data class PendingSyncEntity(
    @PrimaryKey val id: String,
    val entity_type: String,
    val entity_id: String,
    /** INSERT, UPDATE, or DELETE. */
    val action: String,
    /** JSON payload of the change. */
    val payload: String,
    /** PENDING, SYNCED, or FAILED. */
    val status: String = "PENDING",
    val created_at: Long,
    val updated_at: Long,
)
