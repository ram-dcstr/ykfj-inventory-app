package com.ykfj.inventory.util

import android.content.Context
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import java.io.File

/**
 * Wraps the Compressor library to produce two JPEG variants from a source file.
 *
 * - Full: max 1024×1024px, target ~200 KB
 * - Thumb: 200×200px, target ~15 KB
 *
 * Both outputs are placed in [destDir]. If [destDir] does not exist it is created.
 */
class ImageCompressor(private val context: Context) {

    /** Produces a full-size JPEG (~200 KB, max 1024px) in [destDir]. */
    suspend fun compressFull(source: File, destDir: File, fileName: String): File {
        destDir.mkdirs()
        return Compressor.compress(context, source) {
            resolution(1024, 1024)
            quality(85)
            size(200_000)
            format(android.graphics.Bitmap.CompressFormat.JPEG)
        }.also { compressed ->
            val dest = File(destDir, fileName)
            compressed.copyTo(dest, overwrite = true)
            if (compressed.canonicalPath != dest.canonicalPath) compressed.delete()
        }.let { File(destDir, fileName) }
    }

    /** Produces a thumbnail JPEG (~15 KB, 200×200px) in [destDir]. */
    suspend fun compressThumb(source: File, destDir: File, fileName: String): File {
        destDir.mkdirs()
        return Compressor.compress(context, source) {
            resolution(200, 200)
            quality(70)
            size(15_000)
            format(android.graphics.Bitmap.CompressFormat.JPEG)
        }.also { compressed ->
            val dest = File(destDir, fileName)
            compressed.copyTo(dest, overwrite = true)
            if (compressed.canonicalPath != dest.canonicalPath) compressed.delete()
        }.let { File(destDir, fileName) }
    }
}
