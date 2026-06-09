package com.ykfj.inventory.data.remote.sync

import com.ykfj.inventory.data.local.db.YkfjDatabase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.flow.first

fun Route.crudRoutes(db: YkfjDatabase) {
    productCrud(db)
    customerCrud(db)
    metalRateCrud(db)
    categoryCrud(db)
    supplierCrud(db)
    soldRecordCrud(db)
    layawayRecordCrud(db)
    layawayTransactionCrud(db)
    damagedRecordCrud(db)
    paluwaganGroupCrud(db)
    paluwaganSlotCrud(db)
    paluwaganPaymentCrud(db)
}

// ── Products ──────────────────────────────────────────────────────────────────

private fun Route.productCrud(db: YkfjDatabase) {
    route("/api/products") {
        get {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
            val products = db.productDao().getChangedSince(0L).map { it.toSyncDto() }
            call.respond(products.drop(page * size).take(size))
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val product = db.productDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(product.toSyncDto())
        }
        post {
            val dto = call.receive<ProductSyncDto>()
            runCatching { db.productDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<ProductSyncDto>().copy(product_id = id)
            val existing = db.productDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.productDao().update(dto.toEntity().copy(created_at = existing.created_at))
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.productDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Customers ─────────────────────────────────────────────────────────────────

private fun Route.customerCrud(db: YkfjDatabase) {
    route("/api/customers") {
        get {
            val customers = db.customerDao().observeAll().first().map { it.toSyncDto() }
            call.respond(customers)
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val customer = db.customerDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(customer.toSyncDto())
        }
        post {
            val dto = call.receive<CustomerSyncDto>()
            runCatching { db.customerDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<CustomerSyncDto>().copy(customer_id = id)
            db.customerDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.customerDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.customerDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Metal Rates ───────────────────────────────────────────────────────────────

private fun Route.metalRateCrud(db: YkfjDatabase) {
    route("/api/metal-rates") {
        get {
            call.respond(db.metalRateDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val rate = db.metalRateDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(rate.toSyncDto())
        }
        post {
            val dto = call.receive<MetalRateSyncDto>()
            runCatching { db.metalRateDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<MetalRateSyncDto>().copy(rate_id = id)
            db.metalRateDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.metalRateDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.metalRateDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Categories ────────────────────────────────────────────────────────────────

private fun Route.categoryCrud(db: YkfjDatabase) {
    route("/api/categories") {
        get {
            call.respond(db.categoryDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val cat = db.categoryDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(cat.toSyncDto())
        }
        post {
            val dto = call.receive<CategorySyncDto>()
            runCatching { db.categoryDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<CategorySyncDto>().copy(category_id = id)
            db.categoryDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.categoryDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.categoryDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Suppliers ─────────────────────────────────────────────────────────────────

private fun Route.supplierCrud(db: YkfjDatabase) {
    route("/api/suppliers") {
        get {
            call.respond(db.supplierDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val s = db.supplierDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(s.toSyncDto())
        }
        post {
            val dto = call.receive<SupplierSyncDto>()
            runCatching { db.supplierDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<SupplierSyncDto>().copy(supplier_id = id)
            db.supplierDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.supplierDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.supplierDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Sold Records ──────────────────────────────────────────────────────────────

private fun Route.soldRecordCrud(db: YkfjDatabase) {
    route("/api/sold-records") {
        get {
            call.respond(db.soldRecordDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val r = db.soldRecordDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(r.toSyncDto())
        }
        post {
            val dto = call.receive<SoldRecordSyncDto>()
            runCatching { db.soldRecordDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<SoldRecordSyncDto>().copy(sold_id = id)
            db.soldRecordDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.soldRecordDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.soldRecordDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Layaway Records ───────────────────────────────────────────────────────────

private fun Route.layawayRecordCrud(db: YkfjDatabase) {
    route("/api/layaway-records") {
        get {
            call.respond(db.layawayRecordDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val r = db.layawayRecordDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(r.toSyncDto())
        }
        post {
            val dto = call.receive<LayawayRecordSyncDto>()
            runCatching { db.layawayRecordDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<LayawayRecordSyncDto>().copy(layaway_id = id)
            db.layawayRecordDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.layawayRecordDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.layawayRecordDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Layaway Transactions ──────────────────────────────────────────────────────

private fun Route.layawayTransactionCrud(db: YkfjDatabase) {
    route("/api/layaway-transactions") {
        get {
            call.respond(db.layawayTransactionDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val t = db.layawayTransactionDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(t.toSyncDto())
        }
        post {
            val dto = call.receive<LayawayTransactionSyncDto>()
            runCatching { db.layawayTransactionDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<LayawayTransactionSyncDto>().copy(transaction_id = id)
            db.layawayTransactionDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.layawayTransactionDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.layawayTransactionDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Damaged Records ───────────────────────────────────────────────────────────

private fun Route.damagedRecordCrud(db: YkfjDatabase) {
    route("/api/damaged-records") {
        get {
            call.respond(db.damagedRecordDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val r = db.damagedRecordDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(r.toSyncDto())
        }
        post {
            val dto = call.receive<DamagedRecordSyncDto>()
            runCatching { db.damagedRecordDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<DamagedRecordSyncDto>().copy(damaged_id = id)
            db.damagedRecordDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.damagedRecordDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.damagedRecordDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Paluwagan Groups ──────────────────────────────────────────────────────────

private fun Route.paluwaganGroupCrud(db: YkfjDatabase) {
    route("/api/paluwagan-groups") {
        get {
            call.respond(db.paluwaganGroupDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val g = db.paluwaganGroupDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(g.toSyncDto())
        }
        post {
            val dto = call.receive<PaluwaganGroupSyncDto>()
            runCatching { db.paluwaganGroupDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<PaluwaganGroupSyncDto>().copy(group_id = id)
            db.paluwaganGroupDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.paluwaganGroupDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.paluwaganGroupDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Paluwagan Slots ───────────────────────────────────────────────────────────

private fun Route.paluwaganSlotCrud(db: YkfjDatabase) {
    route("/api/paluwagan-slots") {
        get {
            call.respond(db.paluwaganSlotDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val s = db.paluwaganSlotDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(s.toSyncDto())
        }
        post {
            val dto = call.receive<PaluwaganSlotSyncDto>()
            runCatching { db.paluwaganSlotDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<PaluwaganSlotSyncDto>().copy(slot_id = id)
            db.paluwaganSlotDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.paluwaganSlotDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.paluwaganSlotDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

// ── Paluwagan Payments ────────────────────────────────────────────────────────

private fun Route.paluwaganPaymentCrud(db: YkfjDatabase) {
    route("/api/paluwagan-payments") {
        get {
            call.respond(db.paluwaganPaymentDao().getChangedSince(0L).map { it.toSyncDto() })
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val p = db.paluwaganPaymentDao().getById(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(p.toSyncDto())
        }
        post {
            val dto = call.receive<PaluwaganPaymentSyncDto>()
            runCatching { db.paluwaganPaymentDao().insert(dto.toEntity()) }
                .onSuccess { call.respond(HttpStatusCode.Created, dto) }
                .onFailure { call.respond(HttpStatusCode.Conflict, mapOf("error" to it.message)) }
        }
        put("/{id}") {
            val id = call.parameters["id"] ?: return@put call.respond(HttpStatusCode.BadRequest)
            val dto = call.receive<PaluwaganPaymentSyncDto>().copy(payment_id = id)
            db.paluwaganPaymentDao().getById(id) ?: return@put call.respond(HttpStatusCode.NotFound)
            db.paluwaganPaymentDao().update(dto.toEntity())
            call.respond(dto)
        }
        delete("/{id}") {
            val id = call.parameters["id"] ?: return@delete call.respond(HttpStatusCode.BadRequest)
            db.paluwaganPaymentDao().softDelete(id, System.currentTimeMillis())
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
