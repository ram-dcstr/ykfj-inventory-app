package com.ykfj.inventory.data.remote.sync

import com.ykfj.inventory.data.local.db.YkfjDatabase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.syncRoutes(db: YkfjDatabase, deviceId: String) {
    route("/api/sync") {

        get("/status") {
            call.respond(SyncStatusResponse(server_time = System.currentTimeMillis(), device_id = deviceId))
        }

        get("/changes") {
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val now = System.currentTimeMillis()
            call.respond(
                ChangesPayload(
                    products = db.productDao().getChangedSince(since).map { it.toSyncDto() },
                    customers = db.customerDao().getChangedSince(since).map { it.toSyncDto() },
                    sold_records = db.soldRecordDao().getChangedSince(since).map { it.toSyncDto() },
                    layaway_records = db.layawayRecordDao().getChangedSince(since).map { it.toSyncDto() },
                    layaway_transactions = db.layawayTransactionDao().getChangedSince(since).map { it.toSyncDto() },
                    damaged_records = db.damagedRecordDao().getChangedSince(since).map { it.toSyncDto() },
                    metal_rates = db.metalRateDao().getChangedSince(since).map { it.toSyncDto() },
                    categories = db.categoryDao().getChangedSince(since).map { it.toSyncDto() },
                    suppliers = db.supplierDao().getChangedSince(since).map { it.toSyncDto() },
                    users = db.userDao().getChangedSince(since).map { it.toSyncDto() },
                    product_images = db.productImageDao().getChangedSince(since).map { it.toSyncDto() },
                    paluwagan_groups = db.paluwaganGroupDao().getChangedSince(since).map { it.toSyncDto() },
                    paluwagan_slots = db.paluwaganSlotDao().getChangedSince(since).map { it.toSyncDto() },
                    paluwagan_payments = db.paluwaganPaymentDao().getChangedSince(since).map { it.toSyncDto() },
                    activity_logs = db.activityLogDao().getChangedSince(since).map { it.toSyncDto() },
                    gold_purchase_records = db.goldPurchaseRecordDao().getChangedSince(since).map { it.toSyncDto() },
                    gold_purchase_items = db.goldPurchaseItemDao().getChangedSince(since).map { it.toSyncDto() },
                    cash_movements = db.cashMovementDao().getChangedSince(since).map { it.toSyncDto() },
                    server_time = now,
                ),
            )
        }

        post("/push") {
            val payload = runCatching { call.receive<ChangesPayload>() }.getOrElse {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid payload"))
                return@post
            }

            // ── Reference data first (parents of products/transactions) ──────

            // Metal rates
            for (dto in payload.metal_rates) {
                val existing = db.metalRateDao().getById(dto.rate_id)
                if (existing == null) runCatching { db.metalRateDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.metalRateDao().update(dto.toEntity())
            }

            // Categories
            for (dto in payload.categories) {
                val existing = db.categoryDao().getById(dto.category_id)
                if (existing == null) runCatching { db.categoryDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.categoryDao().update(dto.toEntity())
            }

            // Suppliers
            for (dto in payload.suppliers) {
                val existing = db.supplierDao().getById(dto.supplier_id)
                if (existing == null) runCatching { db.supplierDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.supplierDao().update(dto.toEntity())
            }

            // Users — full upsert: insert new users, update existing (incl. password_hash).
            // Both devices are owner-controlled; syncing the bcrypt hash lets phone-created
            // accounts log in on the tablet without a separate provisioning step.
            for (dto in payload.users) {
                val existing = db.userDao().getById(dto.user_id)
                if (existing == null) runCatching { db.userDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.userDao().update(dto.toEntity())
            }

            // Customers
            for (dto in payload.customers) {
                val existing = db.customerDao().getById(dto.customer_id)
                if (existing == null) runCatching { db.customerDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.customerDao().update(dto.toEntity())
            }

            // ── Products & images ────────────────────────────────────────────

            // Products
            for (dto in payload.products) {
                val existing = db.productDao().getById(dto.product_id)
                if (existing == null) runCatching { db.productDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.productDao().update(dto.toEntity())
            }

            // Product images
            for (dto in payload.product_images) {
                val existing = db.productImageDao().getById(dto.image_id)
                if (existing == null || dto.updated_at > existing.updated_at) {
                    runCatching { db.productImageDao().upsert(dto.toEntity()) }
                }
            }

            // ── Transactional records ────────────────────────────────────────

            // Sold records
            for (dto in payload.sold_records) {
                val existing = db.soldRecordDao().getById(dto.sold_id)
                if (existing == null) runCatching { db.soldRecordDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.soldRecordDao().update(dto.toEntity())
            }

            // Layaway records
            for (dto in payload.layaway_records) {
                val existing = db.layawayRecordDao().getById(dto.layaway_id)
                if (existing == null) runCatching { db.layawayRecordDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.layawayRecordDao().update(dto.toEntity())
            }

            // Layaway transactions
            for (dto in payload.layaway_transactions) {
                val existing = db.layawayTransactionDao().getById(dto.transaction_id)
                if (existing == null) runCatching { db.layawayTransactionDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.layawayTransactionDao().update(dto.toEntity())
            }

            // Damaged records
            for (dto in payload.damaged_records) {
                val existing = db.damagedRecordDao().getById(dto.damaged_id)
                if (existing == null) runCatching { db.damagedRecordDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.damagedRecordDao().update(dto.toEntity())
            }

            // Paluwagan groups
            for (dto in payload.paluwagan_groups) {
                val existing = db.paluwaganGroupDao().getById(dto.group_id)
                if (existing == null) runCatching { db.paluwaganGroupDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.paluwaganGroupDao().update(dto.toEntity())
            }

            // Paluwagan slots
            for (dto in payload.paluwagan_slots) {
                val existing = db.paluwaganSlotDao().getById(dto.slot_id)
                if (existing == null) runCatching { db.paluwaganSlotDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.paluwaganSlotDao().update(dto.toEntity())
            }

            // Paluwagan payments
            for (dto in payload.paluwagan_payments) {
                val existing = db.paluwaganPaymentDao().getById(dto.payment_id)
                if (existing == null) runCatching { db.paluwaganPaymentDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.paluwaganPaymentDao().update(dto.toEntity())
            }

            // ── Gold purchases (records before items for FK safety) ──────────

            for (dto in payload.gold_purchase_records) {
                val existing = db.goldPurchaseRecordDao().getById(dto.id)
                if (existing == null) runCatching { db.goldPurchaseRecordDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.goldPurchaseRecordDao().update(dto.toEntity())
            }

            for (dto in payload.gold_purchase_items) {
                val existing = db.goldPurchaseItemDao().getById(dto.id)
                if (existing == null) runCatching { db.goldPurchaseItemDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.goldPurchaseItemDao().update(dto.toEntity())
            }

            // ── Cash movements ───────────────────────────────────────────────

            for (dto in payload.cash_movements) {
                val existing = db.cashMovementDao().getById(dto.id)
                if (existing == null) runCatching { db.cashMovementDao().insert(dto.toEntity()) }
                else if (dto.updated_at > existing.updated_at) db.cashMovementDao().update(dto.toEntity())
            }

            // ── Activity logs (insert-only, immutable rows) ──────────────────

            for (dto in payload.activity_logs) {
                runCatching { db.activityLogDao().insert(dto.toEntity()) }
                // Duplicates fail silently — log_id is unique and we don't want
                // to overwrite an audit row even if a re-push happens.
            }

            call.respond(PushResponse(accepted = true, server_time = System.currentTimeMillis()))
        }
    }
}
