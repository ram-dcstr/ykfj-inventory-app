package com.ykfj.inventory.ui.sold

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoldArchiveScreen(
    viewModel: SoldArchiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var revertTarget by remember { mutableStateOf<SoldRecordRow?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it); viewModel.clearError() }
    }
    // Export confirmation now flows through the global SnackbarController in
    // the ViewModel. Just clear the local field on emission so the user can
    // re-export the same day if they want.
    LaunchedEffect(state.exportedFilename) {
        if (state.exportedFilename != null) viewModel.clearExported()
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DateFilterBar(
                dateMillis = state.selectedDateMillis,
                isExporting = state.isExporting,
                onPickDate = { showDatePicker = true },
                onExport = viewModel::exportPdf,
            )
            HorizontalDivider()
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.records.isEmpty()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No sales for this date.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SummaryCard(
                            totalRevenue = state.totalRevenue,
                            totalCapital = state.totalCapital,
                            totalProfit = state.totalProfit,
                            totalItems = state.totalItems,
                        )
                    }
                    items(items = state.records, key = { it.id }) { row ->
                        SoldRecordCard(
                            row = row,
                            canRevert = state.canRevert,
                            onRevert = { revertTarget = row },
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.selectedDateMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.selectDate(it) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    revertTarget?.let { row ->
        RevertSoldDialog(
            productName = row.productName,
            maxQuantity = row.quantity,
            onConfirm = { qty, reason ->
                viewModel.revert(row.id, qty, reason)
                revertTarget = null
            },
            onDismiss = { revertTarget = null },
        )
    }
}

@Composable
private fun DateFilterBar(
    dateMillis: Long,
    isExporting: Boolean,
    onPickDate: () -> Unit,
    onExport: () -> Unit,
) {
    val label = remember(dateMillis) {
        SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(dateMillis))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPickDate) {
                Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
            }
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onExport, enabled = !isExporting) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.padding(4.dp))
            } else {
                Icon(
                    Icons.Default.PictureAsPdf,
                    contentDescription = "Export PDF",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    totalRevenue: Double,
    totalCapital: Double,
    totalProfit: Double,
    totalItems: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Day Summary",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Items sold", style = MaterialTheme.typography.bodyMedium)
                Text("$totalItems", fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Revenue", style = MaterialTheme.typography.bodyMedium)
                Text(CurrencyFormatter.format(totalRevenue), fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Capital", style = MaterialTheme.typography.bodyMedium)
                Text(CurrencyFormatter.format(totalCapital), fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Profit", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    CurrencyFormatter.format(totalProfit),
                    fontWeight = FontWeight.Bold,
                    color = if (totalProfit >= 0) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SoldRecordCard(
    row: SoldRecordRow,
    canRevert: Boolean,
    onRevert: () -> Unit,
) {
    val timeSdf = remember { SimpleDateFormat("h:mm a", Locale.US) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = row.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                val meta = buildList {
                    row.weightGrams?.let { add("${it}g") }
                    row.size?.let { add("Size $it") }
                    add("Qty ${row.quantity}")
                }.joinToString(" · ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            "Revenue: ${CurrencyFormatter.format(row.totalRevenue)}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Capital: ${CurrencyFormatter.format(row.totalCapital)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Profit: ${CurrencyFormatter.format(row.profit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (row.profit >= 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (row.discountType != DiscountType.NONE) {
                            val discountLabel = when (row.discountType) {
                                DiscountType.FIXED -> "-${CurrencyFormatter.format(row.discountAmount)}"
                                DiscountType.PERCENTAGE -> "-${row.discountAmount}%"
                                else -> ""
                            }
                            Text(
                                text = "Discount: $discountLabel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                        row.customerName?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = timeSdf.format(Date(row.soldDate)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                row.notes?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canRevert) {
                IconButton(onClick = onRevert) {
                    Icon(
                        Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Revert sale",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun RevertSoldDialog(
    productName: String,
    maxQuantity: Int,
    onConfirm: (quantity: Int, reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var qtyText by rememberSaveable { mutableStateOf("1") }
    var reason by rememberSaveable { mutableStateOf("") }
    val qty = qtyText.toIntOrNull() ?: 0
    val qtyError = qty < 1 || qty > maxQuantity
    val isValid = reason.isNotBlank() && !qtyError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revert Sale") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Restore units of \"$productName\" back to available inventory.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantity to revert (1–$maxQuantity)") },
                    isError = qtyText.isNotBlank() && qtyError,
                    supportingText = if (qtyText.isNotBlank() && qtyError) {
                        { Text("Enter 1–$maxQuantity") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    isError = reason.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(qty, reason) },
                enabled = isValid,
            ) { Text("Revert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
