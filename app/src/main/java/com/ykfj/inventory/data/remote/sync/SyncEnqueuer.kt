package com.ykfj.inventory.data.remote.sync

import com.ykfj.inventory.data.local.db.entity.ActivityLogEntity
import com.ykfj.inventory.data.local.db.entity.CashMovementEntity
import com.ykfj.inventory.data.local.db.entity.CategoryEntity
import com.ykfj.inventory.data.local.db.entity.CustomerEntity
import com.ykfj.inventory.data.local.db.entity.DamagedRecordEntity
import com.ykfj.inventory.data.local.db.entity.GoldPurchaseItemEntity
import com.ykfj.inventory.data.local.db.entity.GoldPurchaseRecordEntity
import com.ykfj.inventory.data.local.db.entity.LayawayRecordEntity
import com.ykfj.inventory.data.local.db.entity.LayawayTransactionEntity
import com.ykfj.inventory.data.local.db.entity.MetalRateEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganGroupEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganPaymentEntity
import com.ykfj.inventory.data.local.db.entity.PaluwaganSlotEntity
import com.ykfj.inventory.data.local.db.entity.ProductEntity
import com.ykfj.inventory.data.local.db.entity.ProductImageEntity
import com.ykfj.inventory.data.local.db.entity.SoldRecordEntity
import com.ykfj.inventory.data.local.db.entity.SupplierEntity
import com.ykfj.inventory.data.local.db.entity.UserEntity
import com.ykfj.inventory.domain.sync.PendingSyncManager
import com.ykfj.inventory.domain.sync.SyncAction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between the phone-side repositories and the offline [PendingSyncManager] queue.
 *
 * Every mutating repository method calls one of the `enqueue*` helpers here after
 * the local Room write succeeds. The helper serializes the entity to its Sync DTO
 * as JSON and hands it to [PendingSyncManager], which the [SyncManager] then
 * drains when the tablet is reachable.
 *
 * On the tablet, [PendingSyncManagerImpl] is a no-op — these calls do nothing.
 * So repositories can call them unconditionally without checking device role.
 */
@Singleton
class SyncEnqueuer @Inject constructor(
    private val pendingSyncManager: PendingSyncManager,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun enqueueProduct(e: ProductEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "products", e.product_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueCustomer(e: CustomerEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "customers", e.customer_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueSoldRecord(e: SoldRecordEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "sold_records", e.sold_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueLayawayRecord(e: LayawayRecordEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "layaway_records", e.layaway_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueLayawayTransaction(
        e: LayawayTransactionEntity,
        action: SyncAction = SyncAction.UPDATE,
    ) = pendingSyncManager.enqueue(
        "layaway_transactions", e.transaction_id, action, json.encodeToString(e.toSyncDto()),
    )

    suspend fun enqueueDamagedRecord(e: DamagedRecordEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "damaged_records", e.damaged_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueuePaluwaganGroup(e: PaluwaganGroupEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "paluwagan_groups", e.group_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueuePaluwaganSlot(e: PaluwaganSlotEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "paluwagan_slots", e.slot_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueuePaluwaganPayment(
        e: PaluwaganPaymentEntity,
        action: SyncAction = SyncAction.UPDATE,
    ) = pendingSyncManager.enqueue(
        "paluwagan_payments", e.payment_id, action, json.encodeToString(e.toSyncDto()),
    )

    suspend fun enqueueUser(e: UserEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "users", e.user_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueMetalRate(e: MetalRateEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "metal_rates", e.rate_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueCategory(e: CategoryEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "categories", e.category_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueSupplier(e: SupplierEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "suppliers", e.supplier_id, action, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueProductImage(e: ProductImageEntity, action: SyncAction = SyncAction.UPDATE) =
        pendingSyncManager.enqueue(
            "product_images", e.image_id, action, json.encodeToString(e.toSyncDto()),
        )

    /** Activity logs are insert-only (rows are never updated, only hard-purged after 90 days). */
    suspend fun enqueueActivityLog(e: ActivityLogEntity) =
        pendingSyncManager.enqueue(
            "activity_logs", e.log_id, SyncAction.INSERT, json.encodeToString(e.toSyncDto()),
        )

    suspend fun enqueueGoldPurchaseRecord(
        e: GoldPurchaseRecordEntity,
        action: SyncAction = SyncAction.UPDATE,
    ) = pendingSyncManager.enqueue(
        "gold_purchase_records", e.id, action, json.encodeToString(e.toSyncDto()),
    )

    suspend fun enqueueGoldPurchaseItem(
        e: GoldPurchaseItemEntity,
        action: SyncAction = SyncAction.UPDATE,
    ) = pendingSyncManager.enqueue(
        "gold_purchase_items", e.id, action, json.encodeToString(e.toSyncDto()),
    )

    suspend fun enqueueCashMovement(
        e: CashMovementEntity,
        action: SyncAction = SyncAction.UPDATE,
    ) = pendingSyncManager.enqueue(
        "cash_movements", e.id, action, json.encodeToString(e.toSyncDto()),
    )
}
