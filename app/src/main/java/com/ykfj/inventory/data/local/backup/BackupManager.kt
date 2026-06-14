package com.ykfj.inventory.data.local.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.sqlite.db.SimpleSQLiteQuery
import com.ykfj.inventory.data.local.db.YkfjDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backup format: a ZIP rooted at the app's data folder.
 *
 *  - manifest.txt              single-line `version=1;created_at=<ms>`
 *  - database/ykfj.db          main SQLite file (after WAL checkpoint)
 *  - images/full/&lt;id&gt;.jpg     full-size product images
 *  - images/thumb/&lt;id&gt;.jpg    thumbnails
 *
 * Manual backups are written via [MediaStore] into Downloads so the user can
 * see them in the Files app. Auto backups go to the internal `backups/auto/`
 * directory (DB only) and rotate so only the most recent [AUTO_KEEP] remain.
 *
 * Restore closes Room and relies on the caller to relaunch the process —
 * see [BackupRestoreHelper.restartProcess].
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: YkfjDatabase,
) {
    data class BackupSummary(
        val name: String,
        val sizeBytes: Long,
        val createdAt: Long,
        val type: BackupType,
        val location: String,
    )

    enum class BackupType { MANUAL_FULL, AUTO_DB_ONLY }

    sealed interface CreateResult {
        data class Success(val displayName: String, val sizeBytes: Long) : CreateResult
        data class Failed(val message: String) : CreateResult
    }

    sealed interface RestoreResult {
        /** Restore complete on disk — caller must relaunch the process. */
        data object Success : RestoreResult
        data class InvalidArchive(val message: String) : RestoreResult
        /**
         * Archive comes from a newer schema than this build can handle. Returns
         * the schema versions so the UI can tell the user "update the app first".
         */
        data class IncompatibleSchema(val archiveVersion: Int, val currentVersion: Int) : RestoreResult
        /** The archive is encrypted but no password (or a blank one) was supplied. */
        data object PasswordRequired : RestoreResult
        data class Failed(val message: String) : RestoreResult
    }

    // ── Manual backup → public Downloads ──────────────────────────────────────

    /**
     * Writes a full backup (database + images) to public Downloads.
     *
     * When [password] is non-blank the archive is AES-256-GCM encrypted (see
     * [BackupCrypto]) and saved as `*.ykfjbackup`; restore will require the same
     * password. A blank/null password produces a legacy plaintext `*.zip` — kept
     * for backward compatibility, but the UI nudges users toward a password
     * because the file lands in shared storage where other apps can read it.
     */
    suspend fun createManualBackup(password: String? = null): CreateResult = withContext(Dispatchers.IO) {
        val encrypt = !password.isNullOrBlank()
        runCatching {
            checkpointDatabase()
            val ext = if (encrypt) "ykfjbackup" else "zip"
            val mime = if (encrypt) "application/octet-stream" else "application/zip"
            val fileName = "ykfj-backup-${manualTimestamp()}.$ext"
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext CreateResult.Failed("Could not create file in Downloads")

            try {
                resolver.openOutputStream(uri).use { rawOut ->
                    requireNotNull(rawOut)
                    // For encrypted backups the ZIP is written *through* the cipher
                    // stream, so the manifest and DB are never on disk in the clear.
                    val zipSink = if (encrypt) {
                        BackupCrypto.encryptingStream(password!!.toCharArray(), rawOut)
                    } else {
                        rawOut
                    }
                    ZipOutputStream(zipSink).use { zip ->
                        writeManifest(zip)
                        writeDatabase(zip)
                        writeImages(zip)
                    }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }

            val size = resolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
            recordLastManualBackup(System.currentTimeMillis())
            CreateResult.Success(fileName, size)
        }.getOrElse { CreateResult.Failed(it.message ?: "Unknown error") }
    }

    // ── Auto backup → internal app dir, rotate ────────────────────────────────

    suspend fun createAutoBackup(): CreateResult = withContext(Dispatchers.IO) {
        runCatching {
            checkpointDatabase()
            val dir = autoBackupDir().also { it.mkdirs() }
            val target = File(dir, "ykfj-auto-${autoTimestamp()}.zip")
            ZipOutputStream(target.outputStream()).use { zip ->
                writeManifest(zip)
                writeDatabase(zip)
                // Auto backups are DB-only — images are large; full-backup is manual.
            }
            rotateAutoBackups()
            recordLastAutoBackup(System.currentTimeMillis())
            CreateResult.Success(target.name, target.length())
        }.getOrElse { CreateResult.Failed(it.message ?: "Unknown error") }
    }

    fun listAutoBackups(): List<BackupSummary> {
        val dir = autoBackupDir()
        if (!dir.exists()) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?.map {
                BackupSummary(
                    name = it.name,
                    sizeBytes = it.length(),
                    createdAt = it.lastModified(),
                    type = BackupType.AUTO_DB_ONLY,
                    location = it.absolutePath,
                )
            }
            ?: emptyList()
    }

    fun lastManualBackupAt(): Long =
        prefs().getLong(KEY_LAST_MANUAL, 0L).takeIf { it > 0 } ?: 0L

    fun lastAutoBackupAt(): Long =
        prefs().getLong(KEY_LAST_AUTO, 0L).takeIf { it > 0 } ?: 0L

    // ── Restore ──────────────────────────────────────────────────────────────

    /**
     * Replaces the current database (and images, if present in the archive)
     * with the contents of [archiveUri]. Closes the Room instance before
     * overwriting; the caller MUST relaunch the process — Hilt's singleton
     * graph is invalidated once `close()` is called.
     */
    suspend fun restoreFromZip(archiveUri: Uri, password: String? = null): RestoreResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val encrypted = isEncrypted(archiveUri)
        if (encrypted && password.isNullOrBlank()) return@withContext RestoreResult.PasswordRequired

        // For encrypted archives, decrypt to a temp ZIP first. A wrong password
        // yields garbage that fails ZIP parsing below; either way the live DB is
        // untouched because we stage + validate before closing Room. The temp
        // file is always cleaned up.
        var decryptedTemp: File? = null
        runCatching {
            if (encrypted) {
                val tmp = File(context.cacheDir, "restore-decrypted.zip").apply { delete() }
                decryptedTemp = tmp
                resolver.openInputStream(archiveUri).use { rawIn ->
                    requireNotNull(rawIn) { "Could not open archive" }
                    BackupCrypto.decryptingStream(password!!.toCharArray(), rawIn).use { dec ->
                        tmp.outputStream().use { out -> dec.copyTo(out) }
                    }
                }
            }

            // 1. Stage everything into a temp dir so a partial extract can't corrupt the live data.
            val staging = File(context.cacheDir, "restore-staging").apply {
                deleteRecursively()
                mkdirs()
            }
            val archiveStream = decryptedTemp?.inputStream() ?: resolver.openInputStream(archiveUri)
            archiveStream.use { input ->
                requireNotNull(input) { "Could not open archive" }
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val target = File(staging, entry.name)
                            target.parentFile?.mkdirs()
                            target.outputStream().use { out -> zip.copyTo(out) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            // 2. Validate.
            val stagedDb = File(staging, "database/ykfj.db")
            if (!stagedDb.exists() || stagedDb.length() == 0L) {
                staging.deleteRecursively()
                decryptedTemp?.delete()
                // For an encrypted archive a missing DB almost always means the
                // password was wrong (decryption produced garbage that isn't a ZIP).
                return@withContext if (encrypted) {
                    RestoreResult.Failed("Wrong password, or the backup is corrupted")
                } else {
                    RestoreResult.InvalidArchive("Archive is missing database/ykfj.db")
                }
            }

            val manifest = readManifest(staging)

            // Reject archives written by a newer ZIP layout than this build knows
            // how to read — the entry paths may have moved, so blindly copying
            // database/ykfj.db could grab the wrong file or miss new siblings.
            val archiveFormat = manifest?.formatVersion
            if (archiveFormat != null && archiveFormat > BACKUP_FORMAT_VERSION) {
                staging.deleteRecursively()
                decryptedTemp?.delete()
                return@withContext RestoreResult.InvalidArchive(
                    "Backup uses a newer archive format (v$archiveFormat) than this app supports " +
                        "(v$BACKUP_FORMAT_VERSION). Update the app before restoring.",
                )
            }

            // Cross-check the manifest's schema version against this build's. We
            // reject ONLY downgrades (archive newer than us) — restoring from an
            // older schema is fine because Room runs forward migrations on next
            // open. Without this guard, a v9 backup restored into a v8 install
            // would corrupt silently because Room can't downgrade.
            val archiveSchema = manifest?.schemaVersion
            if (archiveSchema != null && archiveSchema > YkfjDatabase.SCHEMA_VERSION) {
                staging.deleteRecursively()
                decryptedTemp?.delete()
                return@withContext RestoreResult.IncompatibleSchema(
                    archiveVersion = archiveSchema,
                    currentVersion = YkfjDatabase.SCHEMA_VERSION,
                )
            }

            // Decrypted plaintext is no longer needed once it's staged.
            decryptedTemp?.delete()

            // 3. Close Room — singleton graph won't recover, the caller must relaunch.
            database.close()

            val dbFile = context.getDatabasePath(YkfjDatabase.DATABASE_NAME)
            dbFile.parentFile?.mkdirs()
            // Drop existing WAL/SHM so SQLite doesn't replay against the new file.
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
            stagedDb.copyTo(dbFile, overwrite = true)

            // Restore images dir if present; otherwise leave existing images alone.
            val stagedImages = File(staging, "images")
            if (stagedImages.exists() && stagedImages.isDirectory) {
                val imagesDir = File(context.filesDir, "images")
                imagesDir.deleteRecursively()
                stagedImages.copyRecursively(imagesDir, overwrite = true)
            }

            staging.deleteRecursively()
            RestoreResult.Success
        }.getOrElse {
            decryptedTemp?.delete()
            if (encrypted) {
                RestoreResult.Failed("Wrong password, or the backup is corrupted")
            } else {
                RestoreResult.Failed(it.message ?: "Unknown error")
            }
        }
    }

    /**
     * Reads the first bytes of [archiveUri] to detect the [BackupCrypto] magic
     * header. Best-effort: returns false if the file can't be read.
     */
    suspend fun isEncrypted(archiveUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(archiveUri).use { input ->
                requireNotNull(input)
                val header = ByteArray(BackupCrypto.MAGIC_LEN)
                var off = 0
                while (off < header.size) {
                    val read = input.read(header, off, header.size - off)
                    if (read < 0) break
                    off += read
                }
                off == header.size && BackupCrypto.hasMagic(header)
            }
        }.getOrDefault(false)
    }

    /**
     * Best-effort sanity check that an archive has the bits we expect.
     * Encrypted archives can't be inspected without the password, so a valid
     * [BackupCrypto] header is accepted here and verified for real during restore.
     */
    suspend fun peekArchive(archiveUri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (isEncrypted(archiveUri)) return@withContext true
        runCatching {
            val resolver = context.contentResolver
            resolver.openInputStream(archiveUri).use { input ->
                requireNotNull(input)
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "database/ykfj.db") return@runCatching true
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    false
                }
            }
        }.getOrDefault(false)
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun checkpointDatabase() {
        // Force WAL pages into the main file so a plain copy is consistent.
        database.openHelper.writableDatabase.query(
            SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"),
        ).use { /* drain */ while (it.moveToNext()) Unit }
    }

    /**
     * `format=` is the backup-archive layout version (bump when entries change).
     * `schema=` is the Room schema version baked into this DB file — used by
     * [restoreFromZip] to reject downgrades.
     */
    private fun writeManifest(zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry("manifest.txt"))
        val line = "format=$BACKUP_FORMAT_VERSION;schema=${YkfjDatabase.SCHEMA_VERSION};created_at=${System.currentTimeMillis()}\n"
        zip.write(line.toByteArray())
        zip.closeEntry()
    }

    private data class Manifest(
        val formatVersion: Int?,
        val schemaVersion: Int?,
        val createdAt: Long?,
    )

    /** Parses `key=value;key=value` lines tolerant of unknown keys. Returns null if missing. */
    private fun readManifest(stagingDir: File): Manifest? {
        val file = File(stagingDir, "manifest.txt")
        if (!file.exists()) return null
        val text = file.readText().trim()
        val pairs = text.split(';', '\n')
            .mapNotNull { entry ->
                val idx = entry.indexOf('=').takeIf { it > 0 } ?: return@mapNotNull null
                entry.substring(0, idx).trim() to entry.substring(idx + 1).trim()
            }
            .toMap()
        return Manifest(
            // Legacy backups (pre-format key) used `version=1` to mean the format version.
            formatVersion = (pairs["format"] ?: pairs["version"])?.toIntOrNull(),
            schemaVersion = pairs["schema"]?.toIntOrNull(),
            createdAt = pairs["created_at"]?.toLongOrNull(),
        )
    }

    private fun writeDatabase(zip: ZipOutputStream) {
        val dbFile = context.getDatabasePath(YkfjDatabase.DATABASE_NAME)
        if (!dbFile.exists()) return
        zip.putNextEntry(ZipEntry("database/ykfj.db"))
        dbFile.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun writeImages(zip: ZipOutputStream) {
        val root = File(context.filesDir, "images")
        if (!root.exists()) return
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(context.filesDir).path
            zip.putNextEntry(ZipEntry(rel))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun rotateAutoBackups() {
        val dir = autoBackupDir()
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() } ?: return
        files.drop(AUTO_KEEP).forEach { it.delete() }
    }

    private fun autoBackupDir(): File = File(context.filesDir, "backups/auto")

    private fun prefs() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun recordLastManualBackup(millis: Long) {
        prefs().edit().putLong(KEY_LAST_MANUAL, millis).apply()
    }

    private fun recordLastAutoBackup(millis: Long) {
        prefs().edit().putLong(KEY_LAST_AUTO, millis).apply()
    }

    private fun manualTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())

    private fun autoTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())

    companion object {
        const val AUTO_KEEP = 3
        /** Bump only when the ZIP layout (entries, paths) changes. */
        const val BACKUP_FORMAT_VERSION = 1
        private const val PREFS_NAME = "ykfj_backup_prefs"
        private const val KEY_LAST_MANUAL = "last_manual_backup_at"
        private const val KEY_LAST_AUTO = "last_auto_backup_at"
    }
}
