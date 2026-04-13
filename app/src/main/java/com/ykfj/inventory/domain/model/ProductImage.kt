package com.ykfj.inventory.domain.model

/**
 * Metadata for the single image attached to a product. The actual JPEG files
 * are written by `ImageStorageManager` to app-internal storage under
 * `files/images/full/{fileName}` and `files/images/thumb/{fileName}`.
 */
data class ProductImage(
    val id: String,
    val productId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
