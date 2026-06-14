package com.ykfj.inventory.data.remote.sync

import com.ykfj.inventory.data.local.image.ImageStorageManager
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.contentLength
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import com.ykfj.inventory.data.local.db.dao.ProductImageDao

/**
 * Hard limit on a single image upload. ImageCompressor caps the full image at
 * ~200KB and the thumb at ~15KB, so 5MB leaves an enormous margin while still
 * blocking a malicious 1GB POST that would OOM the tablet.
 */
private const val MAX_IMAGE_UPLOAD_BYTES: Long = 5L * 1024L * 1024L

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

            // Reject too-large uploads up front using the Content-Length header,
            // so the malicious bytes are never read into memory. A client lying
            // about its size will still be caught by a second check on the
            // received array below.
            val declaredSize = call.request.contentLength()
            if (declaredSize != null && declaredSize > MAX_IMAGE_UPLOAD_BYTES) {
                call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    mapOf("error" to "Image exceeds ${MAX_IMAGE_UPLOAD_BYTES / 1024}KB limit"),
                )
                return@post
            }

            val record = productImageDao.getById(imageId)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "image metadata not found — push the row first"))

            val bytes = call.receive<ByteArray>()
            if (bytes.size > MAX_IMAGE_UPLOAD_BYTES) {
                call.respond(
                    HttpStatusCode.PayloadTooLarge,
                    mapOf("error" to "Image exceeds ${MAX_IMAGE_UPLOAD_BYTES / 1024}KB limit"),
                )
                return@post
            }
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
