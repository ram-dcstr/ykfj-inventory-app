package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.dao.PendingSyncDao
import com.ykfj.inventory.data.local.db.entity.PendingSyncEntity
import com.ykfj.inventory.domain.sync.DeviceRole
import com.ykfj.inventory.domain.sync.PendingSyncManager
import com.ykfj.inventory.domain.sync.SyncAction
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phone-side implementation of [PendingSyncManager].
 *
 * On the **phone**: every call to [enqueue] writes a row to `pending_sync_queue`.
 * [SyncManager] drains the queue when the tablet is reachable.
 *
 * On the **tablet**: [enqueue] is a no-op — the tablet is the source of truth
 * and has nothing to push. Device role is read from `app_settings` key
 * `device_role`; if absent, defaults to phone behavior (safe: the queue is
 * just never drained on the tablet because [SyncManager] isn't started there).
 */
@Singleton
class PendingSyncManagerImpl @Inject constructor(
    private val pendingSyncDao: PendingSyncDao,
    private val db: YkfjDatabase,
) : PendingSyncManager {

    override suspend fun enqueue(
        entityType: String,
        entityId: String,
        action: SyncAction,
        payloadJson: String,
    ) {
        if (db.appSettingsDao().getValue(DeviceRoleManager.KEY_DEVICE_ROLE) == DeviceRole.TABLET.name) return

        val now = System.currentTimeMillis()
        pendingSyncDao.insert(
            PendingSyncEntity(
                id = UUID.randomUUID().toString(),
                entity_type = entityType,
                entity_id = entityId,
                action = action.name,
                payload = payloadJson,
                status = "PENDING",
                created_at = now,
                updated_at = now,
            ),
        )
    }

}
