package com.ykfj.inventory.domain.usecase.archive

import android.content.Context
import android.net.Uri
import com.ykfj.inventory.data.local.db.dao.CustomerDao
import com.ykfj.inventory.data.local.db.dao.DamagedRecordDao
import com.ykfj.inventory.data.local.db.dao.LayawayRecordDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganGroupDao
import com.ykfj.inventory.data.local.db.dao.ProductDao
import com.ykfj.inventory.data.local.db.dao.SoldRecordDao
import com.ykfj.inventory.data.local.db.dao.UserDao
import com.ykfj.inventory.util.CsvWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Generates a CSV of archived records of one [ArchivableRecordType] within a
 * date range, written to public Downloads. Foreign keys are resolved to
 * readable names so the CSV is useful as a standalone audit artifact.
 *
 * The "date range" filters by each type's natural transaction date:
 *  - SOLD       → `sold_date`
 *  - LAYAWAY    → `created_at`
 *  - DAMAGED    → `date_recorded`
 *  - PALUWAGAN  → `start_date`
 */
class ExportArchiveUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soldDao: SoldRecordDao,
    private val layawayDao: LayawayRecordDao,
    private val damagedDao: DamagedRecordDao,
    private val groupDao: PaluwaganGroupDao,
    private val productDao: ProductDao,
    private val customerDao: CustomerDao,
    private val userDao: UserDao,
) {
    sealed interface Result {
        data class Success(val uri: Uri, val rowCount: Int, val fileName: String) : Result
        data object NoRecords : Result
        data class WriteFailed(val message: String) : Result
    }

    suspend operator fun invoke(
        type: ArchivableRecordType,
        startMillis: Long,
        endMillis: Long,
    ): Result {
        val (header, rows) = when (type) {
            ArchivableRecordType.SOLD -> buildSoldRows(startMillis, endMillis)
            ArchivableRecordType.LAYAWAY -> buildLayawayRows(startMillis, endMillis)
            ArchivableRecordType.DAMAGED -> buildDamagedRows(startMillis, endMillis)
            ArchivableRecordType.PALUWAGAN -> buildPaluwaganRows(startMillis, endMillis)
        }
        if (rows.isEmpty()) return Result.NoRecords

        val csv = CsvWriter.build(header, rows)
        val fileName = "ykfj-archive-${type.csvSlug}-" +
            "${dateFile(startMillis)}-to-${dateFile(endMillis)}.csv"
        val uri = CsvWriter.writeToDownloads(context, fileName, csv)
            ?: return Result.WriteFailed("Could not write to Downloads")
        return Result.Success(uri = uri, rowCount = rows.size, fileName = fileName)
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private suspend fun buildSoldRows(start: Long, end: Long): Pair<List<String>, List<List<String?>>> {
        val records = soldDao.getArchivedInRange(start, end)
        val rows = records.map { r ->
            val product = productDao.getById(r.product_id)
            val customer = r.customer_id?.let { customerDao.getById(it) }
            val seller = userDao.getById(r.sold_by)
            val profit = r.sold_price - r.capital_price
            listOf(
                r.sold_id,
                r.product_id,
                product?.name,
                r.customer_id,
                customer?.name,
                seller?.name,
                r.quantity.toString(),
                r.sold_price.toString(),
                r.capital_price.toString(),
                profit.toString(),
                r.discount_amount.toString(),
                r.discount_type.name,
                dateTime(r.sold_date),
                r.notes,
            )
        }
        return listOf(
            "sold_id", "product_id", "product_name",
            "customer_id", "customer_name", "sold_by",
            "quantity", "sold_price", "capital_price", "profit",
            "discount_amount", "discount_type",
            "sold_date", "notes",
        ) to rows
    }

    private suspend fun buildLayawayRows(start: Long, end: Long): Pair<List<String>, List<List<String?>>> {
        val records = layawayDao.getArchivedInRange(start, end)
        val rows = records.map { r ->
            val product = productDao.getById(r.product_id)
            val customer = customerDao.getById(r.customer_id)
            val createdBy = userDao.getById(r.created_by)
            listOf(
                r.layaway_id,
                r.product_id,
                product?.name,
                r.customer_id,
                customer?.name,
                createdBy?.name,
                r.quantity.toString(),
                r.unit_price.toString(),
                r.total_paid.toString(),
                r.due_date?.let { dateTime(it) },
                r.status.name,
                r.completion_date?.let { dateTime(it) },
                r.forfeited_amount?.toString(),
                dateTime(r.created_at),
            )
        }
        return listOf(
            "layaway_id", "product_id", "product_name",
            "customer_id", "customer_name", "created_by",
            "quantity", "unit_price", "total_paid",
            "due_date", "status", "completion_date", "forfeited_amount",
            "created_at",
        ) to rows
    }

    private suspend fun buildDamagedRows(start: Long, end: Long): Pair<List<String>, List<List<String?>>> {
        val records = damagedDao.getArchivedInRange(start, end)
        val rows = records.map { r ->
            val product = productDao.getById(r.product_id)
            val recordedBy = userDao.getById(r.recorded_by)
            listOf(
                r.damaged_id,
                r.product_id,
                product?.name,
                recordedBy?.name,
                r.reason,
                dateTime(r.date_recorded),
                r.notes,
            )
        }
        return listOf(
            "damaged_id", "product_id", "product_name",
            "recorded_by", "reason", "date_recorded", "notes",
        ) to rows
    }

    private suspend fun buildPaluwaganRows(start: Long, end: Long): Pair<List<String>, List<List<String?>>> {
        val records = groupDao.getArchivedInRange(start, end)
        val rows = records.map { r ->
            listOf(
                r.group_id,
                r.name,
                r.contribution_amount.toString(),
                r.frequency_days.toString(),
                r.total_slots.toString(),
                r.current_round.toString(),
                r.status.name,
                dateTime(r.start_date),
                r.notes,
            )
        }
        return listOf(
            "group_id", "name", "contribution_amount", "frequency_days",
            "total_slots", "current_round", "status", "start_date", "notes",
        ) to rows
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateTimeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private fun dateFile(epochMs: Long): String = dateFmt.format(Date(epochMs))
    private fun dateTime(epochMs: Long): String = dateTimeFmt.format(Date(epochMs))
}
