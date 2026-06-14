package com.ykfj.inventory.domain.usecase.activitylog

import android.content.Context
import android.net.Uri
import com.ykfj.inventory.data.local.db.dao.ActivityLogDao
import com.ykfj.inventory.data.local.db.dao.UserDao
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.util.CsvWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Admin-only (enforced here) CSV export of the activity log between two
 * timestamps. `user_id` is resolved to the user's display name so the CSV
 * stands alone.
 */
class ExportActivityLogUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activityLogDao: ActivityLogDao,
    private val userDao: UserDao,
) {
    sealed interface Result {
        data class Success(val uri: Uri, val rowCount: Int, val fileName: String) : Result
        data object NoRecords : Result
        /** Actor's role is not ADMIN. */
        data object NotAuthorized : Result
        data class WriteFailed(val message: String) : Result
    }

    suspend operator fun invoke(
        startMillis: Long,
        endMillis: Long,
        actorUserId: String,
        userIdFilter: String? = null,
    ): Result {
        val actor = userDao.getById(actorUserId)
        if (actor == null || actor.role != UserRole.ADMIN) return Result.NotAuthorized

        val rows = activityLogDao.getForExport(userIdFilter, startMillis, endMillis)
        if (rows.isEmpty()) return Result.NoRecords

        val nameById = mutableMapOf<String, String>()
        suspend fun resolveName(id: String): String =
            nameById.getOrPut(id) { userDao.getById(id)?.name ?: id }

        val data = rows.map { r ->
            listOf(
                dateTime(r.timestamp),
                r.user_id,
                resolveName(r.user_id),
                r.action.name,
                r.entity_type,
                r.entity_id,
                r.description,
                r.old_value,
                r.new_value,
            )
        }
        val csv = CsvWriter.build(
            header = listOf(
                "timestamp", "user_id", "user_name",
                "action", "entity_type", "entity_id",
                "description", "old_value", "new_value",
            ),
            rows = data,
        )
        val fileName = "ykfj-activity-log-${dateFile(startMillis)}-to-${dateFile(endMillis)}.csv"
        val uri = CsvWriter.writeToDownloads(context, fileName, csv)
            ?: return Result.WriteFailed("Could not write to Downloads")
        return Result.Success(uri = uri, rowCount = rows.size, fileName = fileName)
    }

    private val fileFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private fun dateFile(epochMs: Long): String = fileFmt.format(Date(epochMs))
    private fun dateTime(epochMs: Long): String = dateTimeFmt.format(Date(epochMs))
}
