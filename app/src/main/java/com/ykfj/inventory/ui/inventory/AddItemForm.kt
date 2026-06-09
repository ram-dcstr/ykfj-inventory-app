package com.ykfj.inventory.ui.inventory

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ykfj.inventory.util.CurrencyFormatter
import androidx.core.content.FileProvider
import com.ykfj.inventory.data.local.db.enums.PricingType
import java.io.File

@Composable
fun AddItemForm(
    state: AddItemUiState,
    modifier: Modifier = Modifier,
    onFormDataChanged: (name: String, categoryId: String, metalRateId: String?, weight: Double?) -> Unit,
    onSubmit: (ItemFormParams) -> Unit,
    isSaving: Boolean,
) {
    val existing = state.existingProduct
    val context = LocalContext.current

    // Form state — seeded from existing product on first composition
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var selectedCategoryId by rememberSaveable(existing?.id) { mutableStateOf(existing?.categoryId.orEmpty()) }
    var selectedMetalRateId by rememberSaveable(existing?.id) { mutableStateOf(existing?.metalRateId) }
    var selectedSupplierId by rememberSaveable(existing?.id) { mutableStateOf(existing?.supplierId) }
    var dateAcquired by rememberSaveable(existing?.id) { mutableLongStateOf(existing?.dateAcquired ?: System.currentTimeMillis()) }
    var pricingType by rememberSaveable(existing?.id) { mutableStateOf(existing?.pricingType ?: PricingType.WEIGHTED) }
    // For WEIGHTED items in edit mode, seed capital as per-gram (total ÷ weight)
    var capitalText by rememberSaveable(existing?.id) {
        mutableStateOf(
            if (existing?.pricingType == PricingType.WEIGHTED &&
                existing.weightGrams != null && existing.weightGrams > 0
            ) {
                (existing.capitalPrice / existing.weightGrams).let {
                    if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
                }
            } else {
                existing?.capitalPrice?.toString().orEmpty()
            },
        )
    }
    var sellingText by rememberSaveable(existing?.id) { mutableStateOf(existing?.sellingPrice?.toString().orEmpty()) }
    var weightText by rememberSaveable(existing?.id) { mutableStateOf(existing?.weightGrams?.toString().orEmpty()) }
    var size by rememberSaveable(existing?.id) { mutableStateOf(existing?.size.orEmpty()) }
    var quantityText by rememberSaveable(existing?.id) { mutableStateOf(existing?.quantity?.toString() ?: "1") }
    var notes by rememberSaveable(existing?.id) { mutableStateOf(existing?.notes.orEmpty()) }
    // rememberSaveable so the path survives Activity recreation when the camera app is in foreground
    var pickedImagePath by rememberSaveable { mutableStateOf<String?>(null) }
    val pickedImageFile: File? = pickedImagePath?.let { File(it) }

    val cameraDir = File(context.filesDir, "images/camera").also { it.mkdirs() }
    // Store file path as String — File is not Saveable
    var cameraFilePath by rememberSaveable { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) pickedImagePath = cameraFilePath
    }

    // Permission launcher — fires the camera intent once the permission is granted
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            val f = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
                .also { it.createNewFile() }
            cameraFilePath = f.absolutePath
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
            cameraLauncher.launch(uri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val dest = File(cameraDir, "gallery_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        pickedImagePath = dest.absolutePath
    }

    // Notify VM when form data changes so it can compute the ID preview and weighted price
    val weightDouble = weightText.toDoubleOrNull()
    androidx.compose.runtime.LaunchedEffect(name, selectedCategoryId, selectedMetalRateId, weightDouble) {
        onFormDataChanged(name, selectedCategoryId, selectedMetalRateId, weightDouble)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Photo picker
        ProductImagePicker(
            imageFile = pickedImageFile,
            existingFileName = state.existingImage?.fileName,
            onCamera = {
                val alreadyGranted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
                if (alreadyGranted) {
                    val f = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
                        .also { it.createNewFile() }
                    cameraFilePath = f.absolutePath
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                    cameraLauncher.launch(uri)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onGallery = { galleryLauncher.launch("image/*") },
            onClear = { pickedImagePath = null },
        )

        // Name
        ItemTextField(value = name, onValueChange = { name = it }, label = "Item Name")

        // Product ID preview
        state.productIdPreview?.let {
            Text(
                text = "Item code: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Category dropdown
        CategoryDropdown(
            categories = state.categories,
            selectedId = selectedCategoryId,
            onSelected = { selectedCategoryId = it },
        )

        // Supplier dropdown (optional)
        SupplierDropdown(
            suppliers = state.suppliers,
            selectedId = selectedSupplierId,
            onSelected = { selectedSupplierId = it },
        )

        // Date acquired
        DateAcquiredPicker(
            dateMillis = dateAcquired,
            onDateSelected = { dateAcquired = it },
        )

        // Pricing type toggle
        PricingTypeToggle(
            selected = pricingType,
            onSelected = { pricingType = it },
        )

        // Weighted fields
        if (pricingType == PricingType.WEIGHTED) {
            MetalRateDropdown(
                rates = state.metalRates,
                selectedId = selectedMetalRateId,
                onSelected = { selectedMetalRateId = it },
            )
            ItemDecimalField(value = weightText, onValueChange = { weightText = it }, label = "Weight (grams)")
        }

        // Fixed selling price
        if (pricingType == PricingType.FIXED) {
            ItemDecimalField(value = sellingText, onValueChange = { sellingText = it }, label = "Selling Price (₱)")
        }

        // Capital price — per gram for WEIGHTED, direct total for FIXED
        if (pricingType == PricingType.WEIGHTED) {
            ItemDecimalField(
                value = capitalText,
                onValueChange = { capitalText = it },
                label = "Capital per gram (₱)",
            )
        } else {
            ItemDecimalField(value = capitalText, onValueChange = { capitalText = it }, label = "Capital Price (₱)")
        }

        // Profit summary card — shows capital / selling price / profit at a glance
        val totalCapital: Double? = if (pricingType == PricingType.WEIGHTED) {
            weightDouble?.let { w -> capitalText.toDoubleOrNull()?.let { c -> w * c } }
        } else {
            capitalText.toDoubleOrNull()?.takeIf { it > 0 }
        }
        val sellingPriceValue: Double? = if (pricingType == PricingType.WEIGHTED) {
            val rate = state.metalRates.firstOrNull { it.id == selectedMetalRateId }
            weightDouble?.let { w -> rate?.let { r -> w * r.pricePerGram } }
        } else {
            sellingText.toDoubleOrNull()?.takeIf { it > 0 }
        }
        if (totalCapital != null || sellingPriceValue != null) {
            val profitValue = if (totalCapital != null && sellingPriceValue != null)
                sellingPriceValue - totalCapital else null
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    PriceSummaryColumn(
                        label = "Capital",
                        value = totalCapital?.let { CurrencyFormatter.format(it) } ?: "—",
                        valueColor = Color.Unspecified,
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    PriceSummaryColumn(
                        label = "Selling",
                        value = sellingPriceValue?.let { CurrencyFormatter.format(it) } ?: "—",
                        valueColor = MaterialTheme.colorScheme.primary,
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    PriceSummaryColumn(
                        label = "Profit",
                        value = profitValue?.let { CurrencyFormatter.format(it) } ?: "—",
                        valueColor = when {
                            profitValue == null -> Color.Unspecified
                            profitValue >= 0 -> Color(0xFF1B5E20)
                            else -> MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }

        // Size (optional)
        ItemTextField(value = size, onValueChange = { size = it }, label = "Size (optional)")

        // Quantity
        ItemIntField(value = quantityText, onValueChange = { quantityText = it }, label = "Quantity")

        // Notes
        ItemTextField(value = notes, onValueChange = { notes = it }, label = "Notes (optional)")

        // Save button
        Button(
            onClick = {
                val selectedCategory = state.categories.firstOrNull { it.id == selectedCategoryId }
                val selectedRate = state.metalRates.firstOrNull { it.id == selectedMetalRateId }
                val capitalPerGram = capitalText.toDoubleOrNull() ?: 0.0
                val computedCapital = if (pricingType == PricingType.WEIGHTED) {
                    (weightDouble ?: 0.0) * capitalPerGram
                } else {
                    capitalPerGram
                }
                onSubmit(
                    ItemFormParams(
                        name = name,
                        categoryId = selectedCategoryId,
                        categoryName = selectedCategory?.name.orEmpty(),
                        metalRateId = selectedMetalRateId,
                        metalRateName = selectedRate?.name,
                        supplierId = selectedSupplierId,
                        dateAcquired = dateAcquired,
                        pricingType = pricingType,
                        capitalPrice = computedCapital,
                        sellingPrice = sellingText.toDoubleOrNull(),
                        weightGrams = weightText.toDoubleOrNull(),
                        size = size,
                        quantity = quantityText.toIntOrNull() ?: 1,
                        notes = notes,
                        imageFile = pickedImageFile,
                    ),
                )
            },
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSaving) CircularProgressIndicator() else Text(if (existing == null) "Add Item" else "Save Changes")
        }
    }
}

@Composable
private fun PriceSummaryColumn(label: String, value: String, valueColor: Color) {
    Column(
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}
