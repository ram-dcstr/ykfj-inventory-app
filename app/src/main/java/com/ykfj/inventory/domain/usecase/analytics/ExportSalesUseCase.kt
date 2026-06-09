package com.ykfj.inventory.domain.usecase.analytics

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.CategoryRepository
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import com.ykfj.inventory.util.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class ExportSalesUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soldRecordRepository: SoldRecordRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val categoryRepository: CategoryRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val startMillis: Long,
        val endMillis: Long,
        val actorUserId: String,
    )

    sealed class Result {
        data class Success(val filename: String, val uri: Uri) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result = try {
        val records = soldRecordRepository.observeByDateRange(params.startMillis, params.endMillis).first()

        val products = records.map { it.productId }.distinct()
            .associateWith { productRepository.getById(it) }
        val customers = records.mapNotNull { it.customerId }.distinct()
            .associateWith { customerRepository.getById(it) }
        val categories = categoryRepository.observeAll().first()
            .associateBy { it.id }

        val dateSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val fileSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val startLabel = fileSdf.format(params.startMillis)
        val endLabel = fileSdf.format(params.endMillis)
        val filename = if (startLabel == endLabel) "ykfj-sales-$startLabel.csv"
                       else "ykfj-sales-$startLabel-to-$endLabel.csv"

        val csv = buildString {
            appendLine("Date,Product ID,Product Name,Category,Capital,Sold Price,Profit,Customer")
            for (record in records) {
                val product = products[record.productId]
                val category = product?.categoryId?.let { categories[it] }
                val customer = record.customerId?.let { customers[it] }
                val profit = (record.soldPrice - record.capitalPrice) * record.quantity
                appendLine(
                    listOf(
                        dateSdf.format(record.soldDate),
                        record.productId,
                        product?.name?.csvEscape() ?: "",
                        category?.name?.csvEscape() ?: "",
                        CurrencyFormatter.format(record.capitalPrice * record.quantity),
                        CurrencyFormatter.format(record.soldPrice * record.quantity),
                        CurrencyFormatter.format(profit),
                        customer?.name?.csvEscape() ?: "",
                    ).joinToString(","),
                )
            }
        }

        val resolver = context.contentResolver
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: return Result.Error("Could not create file in Downloads")
        resolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
        cv.clear()
        cv.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, cv, null, null)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.EXPORT,
            description = "Exported sales CSV: $filename (${records.size} records)",
            entityType = "sold_record",
        )
        Result.Success(filename, uri)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Export failed")
    }

    private fun String.csvEscape(): String {
        return if (contains(',') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\"" else this
    }
}
