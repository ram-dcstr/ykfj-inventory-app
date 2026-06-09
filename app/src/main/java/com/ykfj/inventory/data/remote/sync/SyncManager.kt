package com.ykfj.inventory.data.remote.sync

import android.util.Log
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.dao.PendingSyncDao
import com.ykfj.inventory.data.local.db.entity.AppSettingsEntity
import com.ykfj.inventory.data.local.db.entity.ProductImageEntity
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.data.local.image.ImageStorageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates phone ↔ tablet sync:
 * 1. Pull all entities changed since the last sync timestamp
 * 2. Merge them into the local Room DB (last-write-wins by updated_at)
 * 3. Push all PENDING entries from the offline queue
 * 4. Download thumbnails inline; full images in the background
 * 5. Persist the new last-sync timestamp
 *
 * Call [sync] on app open and from the pull-to-refresh UI action.
 * Exposes [status] as a [StateFlow] for the sync status indicator.
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncClient: SyncClient,
    private val db: YkfjDatabase,
    private val nsdDiscovery: NsdDiscovery,
    private val imageStorageManager: ImageStorageManager,
    private val pendingSyncDao: PendingSyncDao,
) {
    data class SyncStatus(
        val lastSyncTime: Long = 0L,
        val pendingCount: Int = 0,
        val isSyncing: Boolean = false,
        val lastError: String? = null,
    )

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    init {
        backgroundScope.launch {
            pendingSyncDao.observePendingCount().collect { count ->
                _status.update { it.copy(pendingCount = count) }
            }
        }
    }

    /**
     * Runs a full sync cycle. Idempotent — a second call while syncing is
     * already in progress is a no-op.
     *
     * On first run (no stored JWT), automatically logs in using credentials
     * saved in [KEY_SYNC_USERNAME] / [KEY_SYNC_PASSWORD] (set via Settings).
     * If the token expires mid-session, the same credentials are used to
     * re-login once before reporting failure.
     */
    suspend fun sync() {
        if (_status.value.isSyncing) return
        _status.update { it.copy(isSyncing = true, lastError = null) }

        try {
            nsdDiscovery.startDiscovery()

            // Always give NSD a brief chance to resolve, even when a Tailscale IP
            // is configured. NSD wins on the same WiFi (faster, zero-config), but
            // if discovery hasn't fired yet we'd otherwise fall straight to the
            // remote Tailscale path and pay its latency. The wait is short enough
            // that emulators (where NSD never resolves) don't suffer much.
            if (nsdDiscovery.discoveredService.value == null) {
                val hasFallback = !db.appSettingsDao()
                    .getValue(ConnectionResolver.KEY_TAILSCALE_IP).isNullOrBlank()
                val waitMs = if (hasFallback) NSD_QUICK_WAIT_MS else NSD_FULL_WAIT_MS
                withTimeoutOrNull(waitMs) {
                    nsdDiscovery.discoveredService.filterNotNull().first()
                }
            }

            if (!ensureAuthenticated()) return

            var since = db.appSettingsDao().getValue(KEY_LAST_SYNC)?.toLongOrNull() ?: 0L

            // Get changes; retry once in case the JWT expired during the wait.
            var changes = syncClient.getChanges(since)
            if (changes == null) {
                db.appSettingsDao().upsert(AppSettingsEntity(key = SyncClient.KEY_JWT_TOKEN, value = ""))
                if (!ensureAuthenticated()) return
                changes = syncClient.getChanges(since)
            }
            if (changes == null) {
                _status.update { it.copy(isSyncing = false, lastError = "Cannot reach tablet") }
                return
            }

            // Recover from clock drift: if our cursor is in the future relative to
            // the server, no row's updated_at will ever exceed it. Reset and re-pull.
            if (since > changes.server_time) {
                Log.w(TAG, "Clock drift: since=$since > server_time=${changes.server_time}; resyncing from 0")
                since = 0L
                changes = syncClient.getChanges(0L) ?: run {
                    _status.update { it.copy(isSyncing = false, lastError = "Cannot reach tablet") }
                    return
                }
            }

            // Push BEFORE merge so any pending local edits land on the server first.
            // That way, when we merge, the server's version already reflects our edits,
            // and the unconditional apply in mergeChanges won't visually revert them.
            pushPending()
            mergeChanges(changes)

            val changedImages = db.productImageDao().getChangedSince(since)
            syncThumbnails(changedImages)
            backgroundScope.launch { syncFullImages(changedImages) }

            // Persist the SERVER's clock as the next-pull cursor. Using the phone's
            // clock breaks sync whenever the phone and tablet drift apart, since the
            // server filters rows with `updated_at > since` in its own clock.
            db.appSettingsDao().upsert(
                AppSettingsEntity(key = KEY_LAST_SYNC, value = changes.server_time.toString())
            )
            _status.update { it.copy(isSyncing = false, lastSyncTime = System.currentTimeMillis()) }
            Log.i(TAG, "Sync complete (since=$since, server_time=${changes.server_time})")
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            _status.update { it.copy(isSyncing = false, lastError = e.message) }
        }
    }

    // ── Authentication ────────────────────────────────────────────────────────

    /** Returns true if a valid JWT is ready; attempts login with stored credentials if not. */
    private suspend fun ensureAuthenticated(): Boolean {
        val token = db.appSettingsDao().getValue(SyncClient.KEY_JWT_TOKEN)
        if (!token.isNullOrBlank()) return true

        val username = db.appSettingsDao().getValue(KEY_SYNC_USERNAME)
        val password = db.appSettingsDao().getValue(KEY_SYNC_PASSWORD)
        if (username.isNullOrBlank() || password.isNullOrBlank()) {
            _status.update { it.copy(isSyncing = false, lastError = "Sync account not configured in Settings") }
            return false
        }

        val result = syncClient.login(username, password)
        if (result.isFailure) {
            _status.update { it.copy(isSyncing = false, lastError = loginErrorMessage(result.exceptionOrNull())) }
            return false
        }
        return true
    }

    private fun loginErrorMessage(err: Throwable?): String = when {
        err is IllegalStateException -> err.message ?: "No tablet IP configured"
        err is io.ktor.client.plugins.HttpRequestTimeoutException ->
            "Connection timed out — check tablet IP and WiFi"
        err is io.ktor.client.plugins.ResponseException ->
            if (err.response.status.value == 401) "Wrong username or password"
            else "Server error: ${err.response.status}"
        err?.message?.contains("refused", ignoreCase = true) == true ->
            "Connection refused — verify the tablet app is open and IP is correct" +
                " (emulator: run 'adb -s <tablet_serial> forward tcp:8080 tcp:8080' first)"
        err?.message?.contains("resolve", ignoreCase = true) == true ||
        err?.message?.contains("hostname", ignoreCase = true) == true ->
            "Cannot resolve host — check the tablet IP address"
        else -> "Login failed: ${err?.message ?: "unknown error"}"
    }

    // ── Merge ─────────────────────────────────────────────────────────────────

    /**
     * Applies the tablet's view of each entity unconditionally — the tablet is
     * the source of truth, so we do NOT compare updated_at before overwriting.
     * That comparison was breaking sync whenever phone/tablet clocks drifted and
     * the phone's seed data ended up with future-dated updated_at values.
     *
     * Phone-originated edits are safe: they're queued in pending_sync_queue and
     * pushed to the server BEFORE mergeChanges runs, so by the time we apply
     * the server's version, it already reflects the phone's push.
     */
    private suspend fun mergeChanges(changes: ChangesPayload) {
        changes.products.forEach { dto ->
            val existing = db.productDao().getById(dto.product_id)
            if (existing == null) runCatching { db.productDao().insert(dto.toEntity()) }
            else db.productDao().update(dto.toEntity())
        }

        changes.customers.forEach { dto ->
            val existing = db.customerDao().getById(dto.customer_id)
            if (existing == null) runCatching { db.customerDao().insert(dto.toEntity()) }
            else db.customerDao().update(dto.toEntity())
        }

        changes.metal_rates.forEach { dto ->
            val existing = db.metalRateDao().getById(dto.rate_id)
            if (existing == null) runCatching { db.metalRateDao().insert(dto.toEntity()) }
            else db.metalRateDao().update(dto.toEntity())
        }

        changes.categories.forEach { dto ->
            val existing = db.categoryDao().getById(dto.category_id)
            if (existing == null) runCatching { db.categoryDao().insert(dto.toEntity()) }
            else db.categoryDao().update(dto.toEntity())
        }

        changes.suppliers.forEach { dto ->
            val existing = db.supplierDao().getById(dto.supplier_id)
            if (existing == null) runCatching { db.supplierDao().insert(dto.toEntity()) }
            else db.supplierDao().update(dto.toEntity())
        }

        changes.sold_records.forEach { dto ->
            val existing = db.soldRecordDao().getById(dto.sold_id)
            if (existing == null) runCatching { db.soldRecordDao().insert(dto.toEntity()) }
            else db.soldRecordDao().update(dto.toEntity())
        }

        changes.layaway_records.forEach { dto ->
            val existing = db.layawayRecordDao().getById(dto.layaway_id)
            if (existing == null) runCatching { db.layawayRecordDao().insert(dto.toEntity()) }
            else db.layawayRecordDao().update(dto.toEntity())
        }

        changes.layaway_transactions.forEach { dto ->
            val existing = db.layawayTransactionDao().getById(dto.transaction_id)
            if (existing == null) runCatching { db.layawayTransactionDao().insert(dto.toEntity()) }
            else db.layawayTransactionDao().update(dto.toEntity())
        }

        changes.damaged_records.forEach { dto ->
            val existing = db.damagedRecordDao().getById(dto.damaged_id)
            if (existing == null) runCatching { db.damagedRecordDao().insert(dto.toEntity()) }
            else db.damagedRecordDao().update(dto.toEntity())
        }

        changes.paluwagan_groups.forEach { dto ->
            val existing = db.paluwaganGroupDao().getById(dto.group_id)
            if (existing == null) runCatching { db.paluwaganGroupDao().insert(dto.toEntity()) }
            else db.paluwaganGroupDao().update(dto.toEntity())
        }

        changes.paluwagan_slots.forEach { dto ->
            val existing = db.paluwaganSlotDao().getById(dto.slot_id)
            if (existing == null) runCatching { db.paluwaganSlotDao().insert(dto.toEntity()) }
            else db.paluwaganSlotDao().update(dto.toEntity())
        }

        changes.paluwagan_payments.forEach { dto ->
            val existing = db.paluwaganPaymentDao().getById(dto.payment_id)
            if (existing == null) runCatching { db.paluwaganPaymentDao().insert(dto.toEntity()) }
            else db.paluwaganPaymentDao().update(dto.toEntity())
        }

        changes.product_images.forEach { dto ->
            db.productImageDao().upsert(dto.toEntity())
        }

        // Users: full upsert (insert new + update existing, password_hash included).
        // Both devices are owner-controlled; syncing the bcrypt hash lets phone-
        // created accounts log in on the tablet without separate provisioning.
        //
        // Blank password_hash from a stale client = "preserve existing". For a
        // brand-new user with no existing row, skip — we can't create a passwordless
        // account.
        changes.users.forEach { dto ->
            val existing = db.userDao().getById(dto.user_id)
            val incoming = if (dto.password_hash.isBlank() && existing != null) {
                dto.copy(password_hash = existing.password_hash)
            } else dto
            if (existing == null) {
                if (incoming.password_hash.isBlank()) return@forEach
                runCatching { db.userDao().insert(incoming.toEntity()) }
            } else {
                db.userDao().update(incoming.toEntity())
            }
        }

        changes.gold_purchase_records.forEach { dto ->
            val existing = db.goldPurchaseRecordDao().getById(dto.id)
            if (existing == null) runCatching { db.goldPurchaseRecordDao().insert(dto.toEntity()) }
            else db.goldPurchaseRecordDao().update(dto.toEntity())
        }

        changes.gold_purchase_items.forEach { dto ->
            val existing = db.goldPurchaseItemDao().getById(dto.id)
            if (existing == null) runCatching { db.goldPurchaseItemDao().insert(dto.toEntity()) }
            else db.goldPurchaseItemDao().update(dto.toEntity())
        }

        changes.cash_movements.forEach { dto ->
            val existing = db.cashMovementDao().getById(dto.id)
            if (existing == null) runCatching { db.cashMovementDao().insert(dto.toEntity()) }
            else db.cashMovementDao().update(dto.toEntity())
        }

        // Activity logs are insert-only and unique by log_id. Duplicates fail
        // silently — never overwrite an audit row.
        changes.activity_logs.forEach { dto ->
            runCatching { db.activityLogDao().insert(dto.toEntity()) }
        }
    }

    // ── Push pending queue ────────────────────────────────────────────────────

    private suspend fun pushPending() {
        val pending = pendingSyncDao.getPending()
        if (pending.isEmpty()) return

        val products = mutableListOf<ProductSyncDto>()
        val customers = mutableListOf<CustomerSyncDto>()
        val soldRecords = mutableListOf<SoldRecordSyncDto>()
        val layawayRecords = mutableListOf<LayawayRecordSyncDto>()
        val layawayTransactions = mutableListOf<LayawayTransactionSyncDto>()
        val damagedRecords = mutableListOf<DamagedRecordSyncDto>()
        val paluwaganGroups = mutableListOf<PaluwaganGroupSyncDto>()
        val paluwaganSlots = mutableListOf<PaluwaganSlotSyncDto>()
        val paluwaganPayments = mutableListOf<PaluwaganPaymentSyncDto>()
        val users = mutableListOf<UserSyncDto>()
        val metalRates = mutableListOf<MetalRateSyncDto>()
        val categories = mutableListOf<CategorySyncDto>()
        val suppliers = mutableListOf<SupplierSyncDto>()
        val productImages = mutableListOf<ProductImageSyncDto>()
        val activityLogs = mutableListOf<ActivityLogSyncDto>()
        val goldPurchaseRecords = mutableListOf<GoldPurchaseRecordSyncDto>()
        val goldPurchaseItems = mutableListOf<GoldPurchaseItemSyncDto>()
        val cashMovements = mutableListOf<CashMovementSyncDto>()

        for (entry in pending) {
            runCatching {
                when (entry.entity_type) {
                    "products" -> products.add(json.decodeFromString(entry.payload))
                    "customers" -> customers.add(json.decodeFromString(entry.payload))
                    "sold_records" -> soldRecords.add(json.decodeFromString(entry.payload))
                    "layaway_records" -> layawayRecords.add(json.decodeFromString(entry.payload))
                    "layaway_transactions" -> layawayTransactions.add(json.decodeFromString(entry.payload))
                    "damaged_records" -> damagedRecords.add(json.decodeFromString(entry.payload))
                    "paluwagan_groups" -> paluwaganGroups.add(json.decodeFromString(entry.payload))
                    "paluwagan_slots" -> paluwaganSlots.add(json.decodeFromString(entry.payload))
                    "paluwagan_payments" -> paluwaganPayments.add(json.decodeFromString(entry.payload))
                    "users" -> users.add(json.decodeFromString(entry.payload))
                    "metal_rates" -> metalRates.add(json.decodeFromString(entry.payload))
                    "categories" -> categories.add(json.decodeFromString(entry.payload))
                    "suppliers" -> suppliers.add(json.decodeFromString(entry.payload))
                    "product_images" -> productImages.add(json.decodeFromString(entry.payload))
                    "activity_logs" -> activityLogs.add(json.decodeFromString(entry.payload))
                    "gold_purchase_records" -> goldPurchaseRecords.add(json.decodeFromString(entry.payload))
                    "gold_purchase_items" -> goldPurchaseItems.add(json.decodeFromString(entry.payload))
                    "cash_movements" -> cashMovements.add(json.decodeFromString(entry.payload))
                    else -> Log.w(TAG, "Unknown entity_type in pending queue: ${entry.entity_type}")
                }
            }.onFailure { Log.w(TAG, "Failed to decode pending entry ${entry.id}", it) }
        }

        val payload = ChangesPayload(
            products = products,
            customers = customers,
            sold_records = soldRecords,
            layaway_records = layawayRecords,
            layaway_transactions = layawayTransactions,
            damaged_records = damagedRecords,
            paluwagan_groups = paluwaganGroups,
            paluwagan_slots = paluwaganSlots,
            paluwagan_payments = paluwaganPayments,
            users = users,
            metal_rates = metalRates,
            categories = categories,
            suppliers = suppliers,
            product_images = productImages,
            activity_logs = activityLogs,
            gold_purchase_records = goldPurchaseRecords,
            gold_purchase_items = goldPurchaseItems,
            cash_movements = cashMovements,
            server_time = System.currentTimeMillis(),
        )

        val response = syncClient.push(payload) ?: run {
            val now = System.currentTimeMillis()
            pending.forEach { pendingSyncDao.updateStatus(it.id, "FAILED", now) }
            Log.w(TAG, "Push failed — ${pending.size} entries marked FAILED")
            return
        }

        if (response.accepted) {
            val now = System.currentTimeMillis()
            pending.forEach { pendingSyncDao.updateStatus(it.id, "SYNCED", now) }
            pendingSyncDao.clearSynced()
            Log.i(TAG, "Pushed ${pending.size} pending entries to tablet")

            // Upload bytes for any product_images we just pushed. Metadata row
            // exists on the tablet now (we pushed it above), so the tablet will
            // accept the upload. Skips deletes — only insert/update need bytes.
            uploadPushedImageBytes(productImages)
        }
    }

    /**
     * Uploads thumb + full JPEG bytes for any product_images we pushed in this
     * cycle, so the tablet has the actual image data, not just the metadata row.
     */
    private suspend fun uploadPushedImageBytes(images: List<ProductImageSyncDto>) {
        if (images.isEmpty()) return
        for (dto in images) {
            if (dto.is_deleted) continue
            val thumbFile = imageStorageManager.thumbFile(dto.file_name)
            val fullFile = imageStorageManager.fullFile(dto.file_name)
            if (thumbFile.exists()) {
                syncClient.uploadImageBytes(dto.image_id, "thumb", thumbFile.readBytes())
            }
            if (fullFile.exists()) {
                syncClient.uploadImageBytes(dto.image_id, "full", fullFile.readBytes())
            }
        }
    }

    // ── Image sync ────────────────────────────────────────────────────────────

    private suspend fun syncThumbnails(images: List<ProductImageEntity>) {
        for (entity in images) {
            if (entity.is_deleted) continue
            val thumbFile = imageStorageManager.thumbFile(entity.file_name)
            if (thumbFile.exists()) continue
            val bytes = syncClient.downloadImageBytes(entity.image_id, "thumb") ?: continue
            thumbFile.writeBytes(bytes)
        }
    }

    private suspend fun syncFullImages(images: List<ProductImageEntity>) {
        for (entity in images) {
            if (entity.is_deleted) continue
            val fullFile = imageStorageManager.fullFile(entity.file_name)
            if (fullFile.exists()) continue
            val bytes = syncClient.downloadImageBytes(entity.image_id, "full") ?: continue
            fullFile.writeBytes(bytes)
        }
    }

    companion object {
        const val KEY_LAST_SYNC = "last_sync_timestamp"
        const val KEY_SYNC_USERNAME = "sync_username"
        const val KEY_SYNC_PASSWORD = "sync_password"
        private const val TAG = "SyncManager"

        // Brief wait when a Tailscale fallback exists — long enough for an
        // already-listening tablet on the same WiFi to be discovered, short
        // enough not to delay remote syncs significantly.
        private const val NSD_QUICK_WAIT_MS = 1_500L
        // Longer wait when NSD is the only option, since blocking is cheap then.
        private const val NSD_FULL_WAIT_MS = 3_000L
    }
}
