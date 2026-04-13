package com.ykfj.inventory.domain.sync

/**
 * Offline change queue gateway. Every mutating repository on the **phone**
 * calls [enqueue] after a successful local write so the change can be
 * pushed to the tablet once connectivity returns.
 *
 * On the **tablet** (source of truth) every call is a no-op — the tablet
 * has nothing to queue. Device role is resolved internally by the
 * implementation from `app_settings`.
 *
 * The actual push loop is owned by `SyncManager` in Phase 5.3; this
 * interface exists in Phase 1.4 so repository impls can depend on it
 * without knowing about networking.
 */
interface PendingSyncManager {

    /** Enqueues one logical mutation for the given entity. */
    suspend fun enqueue(
        entityType: String,
        entityId: String,
        action: SyncAction,
        payloadJson: String,
    )
}

/** Mutations that can be queued for later push to the tablet. */
enum class SyncAction { INSERT, UPDATE, DELETE }
