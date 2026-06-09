package com.ykfj.inventory.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore

/**
 * Writes a CSV file directly into the device's public Downloads folder via
 * [MediaStore]. minSdk is 31, so scoped storage applies — the user can find
 * the resulting file in Files / Downloads without granting any permission.
 *
 * The file content is composed in memory; archive exports here are bounded
 * by date range and won't realistically grow large enough to need streaming.
 */
object CsvWriter {

    /** Properly escapes a single CSV field per RFC 4180. */
    fun escape(value: String?): String {
        if (value == null) return ""
        val needsQuoting = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuoting) "\"$escaped\"" else escaped
    }

    /** Builds a CSV string from a header row and any number of data rows. */
    fun build(header: List<String>, rows: List<List<String?>>): String = buildString {
        append(header.joinToString(",", postfix = "\n") { escape(it) })
        rows.forEach { row ->
            append(row.joinToString(",", postfix = "\n") { escape(it) })
        }
    }

    /**
     * Writes [content] to a new file in Downloads with the given [fileName].
     * Returns the inserted [Uri] on success, or `null` if the system rejected
     * the insert (e.g. duplicate name with no override permission).
     */
    fun writeToDownloads(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String = "text/csv",
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return runCatching {
            resolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse {
            resolver.delete(uri, null, null)
            null
        }
    }
}
