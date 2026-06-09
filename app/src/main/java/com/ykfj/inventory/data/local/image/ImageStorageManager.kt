package com.ykfj.inventory.data.local.image

import android.content.Context
import com.ykfj.inventory.domain.model.ProductImage
import com.ykfj.inventory.domain.repository.ProductImageRepository
import com.ykfj.inventory.util.ImageCompressor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes two JPEG variants per product image and persists the metadata record:
 * - Full: `{filesDir}/images/full/{imageId}.jpg`  (~200 KB, max 1024px)
 * - Thumb: `{filesDir}/images/thumb/{imageId}.jpg` (~15 KB, 200×200)
 *
 * Callers supply a raw [source] file (camera capture or gallery copy) and this
 * manager handles compression + DB record creation.
 */
@Singleton
class ImageStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val productImageRepository: ProductImageRepository,
) {
    private val fullDir get() = File(context.filesDir, "images/full").also { it.mkdirs() }
    private val thumbDir get() = File(context.filesDir, "images/thumb").also { it.mkdirs() }
    private val compressor = ImageCompressor(context)

    /** Camera temp dir — used to place capture files before processing. */
    val cameraDir get() = File(context.filesDir, "images/camera").also { it.mkdirs() }

    fun fullFile(fileName: String) = File(fullDir, fileName)
    fun thumbFile(fileName: String) = File(thumbDir, fileName)

    /**
     * Compresses [source] into full + thumb variants, writes them to internal
     * storage, and upserts the [ProductImage] metadata record.
     *
     * @return the saved [ProductImage] metadata
     */
    suspend fun saveImage(productId: String, source: File): ProductImage {
        val imageId = UUID.randomUUID().toString()
        val fileName = "$imageId.jpg"
        val now = System.currentTimeMillis()

        compressor.compressFull(source, fullDir, fileName)
        val thumb = compressor.compressThumb(source, thumbDir, fileName)

        val image = ProductImage(
            id = imageId,
            productId = productId,
            fileName = fileName,
            fileSizeBytes = thumb.length(),
            createdAt = now,
            updatedAt = now,
        )
        productImageRepository.upsert(image)
        return image
    }

    /** Soft-deletes the DB record and removes the physical files. */
    suspend fun deleteImage(image: ProductImage) {
        productImageRepository.delete(image.id)
        fullFile(image.fileName).delete()
        thumbFile(image.fileName).delete()
    }
}
