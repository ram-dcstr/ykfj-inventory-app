package com.ykfj.inventory.domain.usecase.sold

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import com.itextpdf.text.BaseColor
import com.itextpdf.text.Chunk
import com.itextpdf.text.Document
import com.itextpdf.text.Element
import com.itextpdf.text.Font
import com.itextpdf.text.PageSize
import com.itextpdf.text.Paragraph
import com.itextpdf.text.Phrase
import com.itextpdf.text.pdf.PdfPCell
import com.itextpdf.text.pdf.PdfPTable
import com.itextpdf.text.pdf.PdfWriter
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.domain.repository.AppSettingsRepository
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import com.ykfj.inventory.util.CurrencyFormatter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

class ExportDailySalesPdfUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val soldRecordRepository: SoldRecordRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(val dateMillis: Long, val actorUserId: String)

    sealed class Result {
        data class Success(val filename: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend operator fun invoke(params: Params): Result = try {
        val startOfDay = GetSoldRecordsUseCase.startOfDay(params.dateMillis)
        val endOfDay = startOfDay + 24 * 60 * 60 * 1_000L - 1

        val records = soldRecordRepository.observeByDateRange(startOfDay, endOfDay).first()
        val products = records.map { it.productId }.distinct()
            .associateWith { productRepository.getById(it) }
        val customers = records.mapNotNull { it.customerId }.distinct()
            .associateWith { customerRepository.getById(it) }

        val password = appSettingsRepository.getValue("daily_export_password") ?: "ykfj2024"
        val displaySdf = SimpleDateFormat("MMMM d, yyyy", Locale.US)
        val fileSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val filename = "ykfj-sales-${fileSdf.format(params.dateMillis)}.pdf"

        val baos = ByteArrayOutputStream()
        val document = Document(PageSize.A4.rotate())
        val writer = PdfWriter.getInstance(document, baos)
        writer.setEncryption(
            password.toByteArray(Charsets.UTF_8),
            password.toByteArray(Charsets.UTF_8),
            PdfWriter.ALLOW_PRINTING or PdfWriter.ALLOW_COPY,
            PdfWriter.STANDARD_ENCRYPTION_128,
        )
        document.open()

        val titleFont = Font(Font.FontFamily.HELVETICA, 16f, Font.BOLD)
        val subtitleFont = Font(Font.FontFamily.HELVETICA, 11f, Font.NORMAL)
        val headerFont = Font(Font.FontFamily.HELVETICA, 9f, Font.BOLD)
        val cellFont = Font(Font.FontFamily.HELVETICA, 8f, Font.NORMAL)
        val summaryBoldFont = Font(Font.FontFamily.HELVETICA, 10f, Font.BOLD)
        val summaryFont = Font(Font.FontFamily.HELVETICA, 10f, Font.NORMAL)

        document.add(Paragraph("YKFJ Gold Jewelry — Daily Sales Report", titleFont))
        document.add(Paragraph(displaySdf.format(params.dateMillis), subtitleFont))
        document.add(Chunk.NEWLINE)

        if (records.isEmpty()) {
            document.add(Paragraph("No sales recorded for this date.", cellFont))
        } else {
            // 9 columns: Product | Grams | Size | Qty | Capital/u | Sell/u | Discount | Profit | Customer
            val table = PdfPTable(9)
            table.widthPercentage = 100f
            table.setWidths(floatArrayOf(3f, 1f, 1f, 0.8f, 1.5f, 1.5f, 1.5f, 1.5f, 2f))

            listOf("Product", "Grams", "Size", "Qty", "Capital/u", "Sell/u", "Discount", "Profit", "Customer")
                .forEach { label ->
                    table.addCell(
                        PdfPCell(Phrase(label, headerFont)).apply {
                            backgroundColor = BaseColor.LIGHT_GRAY
                            horizontalAlignment = Element.ALIGN_CENTER
                            setPadding(4f)
                        },
                    )
                }

            var totalRevenue = 0.0
            var totalCapital = 0.0
            var totalItems = 0

            for (record in records) {
                val product = products[record.productId]
                val customer = record.customerId?.let { customers[it] }
                val rowTotal = record.soldPrice * record.quantity
                val rowCapital = record.capitalPrice * record.quantity
                totalRevenue += rowTotal
                totalCapital += rowCapital
                totalItems += record.quantity

                val discountStr = when (record.discountType) {
                    DiscountType.NONE -> "—"
                    DiscountType.FIXED -> CurrencyFormatter.format(record.discountAmount)
                    DiscountType.PERCENTAGE -> "%.1f%%".format(record.discountAmount)
                }

                fun cell(text: String, align: Int = Element.ALIGN_LEFT) =
                    PdfPCell(Phrase(text, cellFont)).apply {
                        horizontalAlignment = align; setPadding(3f)
                    }

                table.addCell(cell(product?.name ?: record.productId))
                table.addCell(cell(product?.weightGrams?.let { "%.2f".format(it) } ?: "—", Element.ALIGN_RIGHT))
                table.addCell(cell(product?.size ?: "—", Element.ALIGN_CENTER))
                table.addCell(cell(record.quantity.toString(), Element.ALIGN_CENTER))
                table.addCell(cell(CurrencyFormatter.format(record.capitalPrice), Element.ALIGN_RIGHT))
                table.addCell(cell(CurrencyFormatter.format(record.soldPrice), Element.ALIGN_RIGHT))
                table.addCell(cell(discountStr, Element.ALIGN_RIGHT))
                table.addCell(cell(CurrencyFormatter.format(rowTotal - rowCapital), Element.ALIGN_RIGHT))
                table.addCell(cell(customer?.name ?: "—"))
            }

            document.add(table)
            document.add(Chunk.NEWLINE)

            val totalProfit = totalRevenue - totalCapital
            document.add(Paragraph("─────────────────────────────────────────────────", summaryFont))
            document.add(Paragraph("Summary", summaryBoldFont))
            document.add(Paragraph("Total items sold : $totalItems", summaryFont))
            document.add(Paragraph("Total revenue    : ${CurrencyFormatter.format(totalRevenue)}", summaryFont))
            document.add(Paragraph("Total capital    : ${CurrencyFormatter.format(totalCapital)}", summaryFont))
            document.add(Paragraph("Total profit     : ${CurrencyFormatter.format(totalProfit)}", summaryBoldFont))
        }
        document.close()

        val resolver = context.contentResolver
        val cv = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: return Result.Error("Could not create file in Downloads")
        resolver.openOutputStream(uri)?.use { it.write(baos.toByteArray()) }
        cv.clear()
        cv.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, cv, null, null)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.EXPORT,
            description = "Exported daily sales PDF: $filename",
            entityType = "sold_record",
        )
        Result.Success(filename)
    } catch (e: Exception) {
        Result.Error(e.message ?: "Export failed")
    }
}
