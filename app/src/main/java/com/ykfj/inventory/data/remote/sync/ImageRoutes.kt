package com.ykfj.inventory.data.remote.sync

import com.ykfj.inventory.data.local.image.ImageStorageManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import com.ykfj.inventory.data.local.db.dao.ProductImageDao

fun Route.imageRoutes(imageStorageManager: ImageStorageManager, productImageDao: ProductImageDao) {
    route("/api/images") {

        /**
         * Serve a product image.
         * GET /api/images/{image_id}?type=thumb|full  (default: full)
         *
         * Returns raw JPEG bytes. Phone caches locally after first download.
         */
        get("/{image_id}") {
            val imageId = call.parameters["image_id"]
                ?: return@get call.respond(HttpStatusCode.BadRequest)
            val useThumb = call.request.queryParameters["type"] == "thumb"

            val record = productImageDao.getById(imageId)
                ?: return@get call.respond(HttpStatusCode.NotFound)

            val file = if (useThumb) imageStorageManager.thumbFile(record.file_name)
                       else imageStorageManager.fullFile(record.file_name)

            if (!file.exists()) return@get call.respond(HttpStatusCode.NotFound)
            call.respondBytes(file.readBytes(), ContentType.Image.JPEG)
        }

        /**
         * Receive a product image uploaded from the phone.
         * POST /api/images/{image_id}?type=thumb|full
         * Body: raw JPEG bytes
         *
         * The metadata row must already exist (sent via /sync/push); this just
         * writes the bytes to disk under the row's file_name. If the file already
         * exists, it's overwritten — the phone is authoritative for an image it
         * created (it has the highest-fidelity source bytes).
         */
        post("/{image_id}") {
            val imageId = call.parameters["image_id"]
                ?: return@post call.respond(HttpStatusCode.BadRequest)
            val useThumb = call.request.queryParameters["type"] == "thumb"

            val record = productImageDao.getById(imageId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "image metadata not found — push the row first"))

            val bytes = call.receive<ByteArray>()
            val file = if (useThumb) imageStorageManager.thumbFile(record.file_name)
                       else imageStorageManager.fullFile(record.file_name)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            call.respond(HttpStatusCode.NoContent)
        }

        /**
         * Delta image discovery.
         * GET /api/images/sync?since={timestamp}
         *
         * Returns list of image_ids changed since the given timestamp.
         * Phone downloads only new/updated images.
         */
        get("/sync") {
            val since = call.request.queryParameters["since"]?.toLongOrNull() ?: 0L
            val changed = productImageDao.getChangedSince(since).map { it.image_id }
            call.respond(changed)
        }
    }
}
