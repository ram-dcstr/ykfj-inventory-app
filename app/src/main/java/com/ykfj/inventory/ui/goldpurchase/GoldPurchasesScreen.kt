package com.ykfj.inventory.ui.goldpurchase

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPurchasesScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToDetail: (id: String) -> Unit,
    viewModel: GoldPurchasesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingDeleteRow by remember { mutableStateOf<GoldPurchaseItemRow?>(null) }
    var showBulkSellDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.deleteError) {
        state.deleteError?.let { snackbarHost.showSnackbar(it); viewModel.clearDeleteError() }
    }
    LaunchedEffect(state.bulkError) {
        state.bulkError?.let { snackbarHost.showSnackbar(it); viewModel.clearBulkError() }
    }

    Scaffold(
        topBar = {
            if (state.isInSelectMode) {
                TopAppBar(
                    title = { Text("${state.selectedItemIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = { showBulkSellDialog = true },
                            enabled = state.selectedSellableRows.isNotEmpty(),
                        ) { Text("Sell to supplier") }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (!state.isInSelectMode) {
                FloatingActionButton(onClick = onNavigateToAdd) {
                    Icon(Icons.Default.Add, contentDescription = "Add Gold Purchase")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search by item, purity, customer, or date…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )

            FilterRow(
                selected = state.filter,
                availablePurities = state.availablePurities,
                onSelected = viewModel::setFilter,
            )
            HorizontalDivider()

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.filtered.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (state.rows.isEmpty()) "No purchases recorded yet"
                        else "No items match the current filter / search",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.filtered, key = { it.itemId }) { row ->
                        GoldPurchaseItemRowCard(
                            row = row,
                            canDelete = state.canDelete,
                            isInSelectMode = state.isInSelectMode,
                            isSelected = row.itemId in state.selectedItemIds,
                            onClick = {
                                if (state.isInSelectMode) viewModel.toggleSelection(row.itemId)
                                else onNavigateToDetail(row.recordId)
                            },
                            onLongPress = { viewModel.toggleSelection(row.itemId) },
                            onDelete = { pendingDeleteRow = row },
                        )
                    }
                }
            }
        }
    }

    pendingDeleteRow?.let { row ->
        DeletePurchaseDialog(
            row = row,
            onConfirm = { reason ->
                viewModel.deletePurchase(row.recordId, reason)
                pendingDeleteRow = null
            },
            onDismiss = { pendingDeleteRow = null },
        )
    }

    if (showBulkSellDialog) {
        BulkSellDialog(
            itemCount = state.selectedSellableRows.size,
            totalWeight = state.selectedTotalWeight,
            totalPaid = state.selectedTotalPaid,
            onConfirm = { pricePerGram ->
                showBulkSellDialog = false
                viewModel.bulkSellToSupplier(pricePerGram)
            },
            onDismiss = { showBulkSellDialog = false },
        )
    }
}

@Composable
private fun FilterRow(
    selected: GoldPurchaseFilter,
    availablePurities: List<String>,
    onSelected: (GoldPurchaseFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterPill(label = "All", isSelected = selected is GoldPurchaseFilter.All) {
            onSelected(GoldPurchaseFilter.All)
        }
        FilterPill(label = "In stock", isSelected = selected is GoldPurchaseFilter.InStock) {
            onSelected(GoldPurchaseFilter.InStock)
        }
        FilterPill(label = "Sold", isSelected = selected is GoldPurchaseFilter.Sold) {
            onSelected(GoldPurchaseFilter.Sold)
        }
        availablePurities.forEach { purity ->
            FilterPill(
                label = purity,
                isSelected = selected is GoldPurchaseFilter.Purity && selected.value == purity,
            ) { onSelected(GoldPurchaseFilter.Purity(purity)) }
        }
    }
}

@Composable
private fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GoldPurchaseItemRowCard(
    row: GoldPurchaseItemRow,
    canDelete: Boolean,
    isInSelectMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = if (isSelected) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) else CardDefaults.cardColors(),
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isInSelectMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle
                    else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
            } else {
                Box(modifier = Modifier.size(0.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        row.description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    StatusChip(row = row)
                    if (row.isTradeIn) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Trade-in", style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                    if (canDelete && !isInSelectMode && !row.parentHasSoldItems) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete purchase",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.purity?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        "${formatGrams(row.weightGrams)}g",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            row.customerName ?: "Walk-in",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            row.dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            CurrencyFormatter.format(row.finalValue),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        row.supplierProfit?.let { profit ->
                            Text(
                                "Profit: ${CurrencyFormatter.format(profit)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (profit >= 0) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(row: GoldPurchaseItemRow) {
    if (row.isSoldToSupplier) {
        AssistChip(
            onClick = {},
            label = { Text("Sold", style = MaterialTheme.typography.labelSmall) },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
        )
    } else {
        AssistChip(
            onClick = {},
            label = { Text("In stock", style = MaterialTheme.typography.labelSmall) },
        )
    }
}

@Composable
private fun BulkSellDialog(
    itemCount: Int,
    totalWeight: Double,
    totalPaid: Double,
    onConfirm: (pricePerGram: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var pricePerGramText by rememberSaveable { mutableStateOf("") }
    val pricePerGram = pricePerGramText.toDoubleOrNull()
    val totalReceived = pricePerGram?.let { it * totalWeight }
    val profit = totalReceived?.let { it - totalPaid }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell $itemCount item${if (itemCount == 1) "" else "s"} to supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Total weight: ${formatGrams(totalWeight)}g",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "You paid: ${CurrencyFormatter.format(totalPaid)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                OutlinedTextField(
                    value = pricePerGramText,
                    onValueChange = { pricePerGramText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Supplier rate (₱/g) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (totalReceived != null) {
                    Text(
                        "Supplier pays: ${CurrencyFormatter.format(totalReceived)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (profit != null) {
                    Text(
                        "Profit: ${CurrencyFormatter.format(profit)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (profit >= 0) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { pricePerGram?.let { onConfirm(it) } },
                enabled = pricePerGram != null && pricePerGram > 0 && itemCount > 0,
            ) { Text("Mark Sold") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeletePurchaseDialog(
    row: GoldPurchaseItemRow,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Purchase?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This will remove the entire purchase record (${row.customerName ?: "Walk-in"} • ${row.dateLabel}) and all of its items. Provide a reason (required).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatGrams(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)
