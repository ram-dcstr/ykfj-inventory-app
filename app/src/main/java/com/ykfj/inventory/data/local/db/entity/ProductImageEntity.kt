package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One image per product. A `UNIQUE` index on [product_id] enforces the 1:1
 * relationship — there is intentionally no `display_order` column.
 *
 * Two physical JPEGs are written per image by [ImageStorageManager]:
 * - Full: `files/images/full/{file_name}` (~200 KB, max 1024px)
 * - Thumb: `files/images/thumb/{file_name}` (~15 KB, 200×200)
 */
@Entity(
    tableName = "product_images",
    indices = [
        Index(value = ["product_id"], unique = true),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class ProductImageEntity(
    @PrimaryKey val image_id: String,
    val product_id: String,
    val file_name: String,
    val file_size_bytes: Long,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
