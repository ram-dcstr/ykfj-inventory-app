package com.ykfj.inventory.ui.goldpurchase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ykfj.inventory.domain.model.GoldPurchaseItem
import com.ykfj.inventory.util.CurrencyFormatter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SoldDateSdf = SimpleDateFormat("MMM d, yyyy", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoldPurchaseDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: GoldPurchaseDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showRevertDialog by rememberSaveable { mutableStateOf(false) }
    var showRevertSoldDialog by rememberSaveable { mutableStateOf(false) }
    var showRevertTradeInDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.isReverted) { if (state.isReverted) onNavigateUp() }
    LaunchedEffect(state.revertError) {
        state.revertError?.let { snackbarHost.showSnackbar(it); viewModel.clearRevertError() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Purchase Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.record == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Purchase not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                val record = state.record!!
                val dateSdf = remember { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.isTradeIn) {
                        SuggestionChip(
                            onClick = {},
                            label = {
                                Text(
                                    "Trade-in — linked to a sale",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }

                    // Header card
                    Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                state.customerName ?: "Walk-in",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                dateSdf.format(Date(record.paidAt)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Total paid", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    CurrencyFormatter.format(record.totalPaid),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            if (!record.notes.isNullOrBlank()) {
                                Text(
                                    "Notes: ${record.notes}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Items
                    Text(
                        "Items (${state.items.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    state.items.forEachIndexed { index, item ->
                        PurchaseItemCard(
                            index = index,
                            item = item,
                            canUnmarkItem = state.canUnmarkItem,
                            onMarkSold = { price -> viewModel.markItemSoldToSupplier(item.id, price) },
                            onUndoSold = { viewModel.unmarkItemSoldToSupplier(item.id) },
                        )
                    }

                    // Record-level revert action — at most one shown at a time:
                    //  - canRevertSold (admin, has supplier-sold items): revert the supplier sales first
                    //  - canRevertTradeIn (admin/manager, linked to a sale, no supplier-sold items):
                    //    atomic revert of purchase + sale together
                    //  - canRevert (admin, plain purchase, no supplier-sold items): soft-delete purchase
                    when {
                        state.canRevertSold -> {
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { showRevertSoldDialog = true },
                                enabled = !state.isReverting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isReverting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onTertiary,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text("Revert Sold (${state.soldItemCount})")
                                }
                            }
                        }
                        state.canRevertTradeIn -> {
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { showRevertTradeInDialog = true },
                                enabled = !state.isReverting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isReverting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onError,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text("Revert Trade-in")
                                }
                            }
                        }
                        state.canRevert -> {
                            Spacer(Modifier.height(4.dp))
                            Button(
                                onClick = { showRevertDialog = true },
                                enabled = !state.isReverting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isReverting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = MaterialTheme.colorScheme.onError,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text("Revert Purchase")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRevertDialog) {
        RevertConfirmDialog(
            onConfirm = { reason ->
                showRevertDialog = false
                viewModel.revert(reason)
            },
            onDismiss = { showRevertDialog = false },
        )
    }

    if (showRevertSoldDialog) {
        RevertSoldDialog(
            count = state.soldItemCount,
            onConfirm = {
                showRevertSoldDialog = false
                viewModel.revertAllSold()
            },
            onDismiss = { showRevertSoldDialog = false },
        )
    }

    if (showRevertTradeInDialog) {
        RevertTradeInConfirmDialog(
            onConfirm = { reason ->
                showRevertTradeInDialog = false
                viewModel.revertTradeIn(reason)
            },
            onDismiss = { showRevertTradeInDialog = false },
        )
    }
}

@Composable
private fun PurchaseItemCard(
    index: Int,
    item: GoldPurchaseItem,
    canUnmarkItem: Boolean,
    onMarkSold: (price: Double) -> Unit,
    onUndoSold: () -> Unit,
) {
    val context = LocalContext.current
    val thumbFile = item.photoFilename?.let {
        File(context.filesDir, "images/thumb/$it")
    }
    var showSellDialog by rememberSaveable { mutableStateOf(false) }

    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Item ${index + 1}: ${item.description}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (item.isSoldToSupplier) {
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
            if (item.purity != null) {
                Text(
                    "Purity: ${item.purity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Weight: ${item.weightGrams}g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Buy rate: ${CurrencyFormatter.format(item.buyRatePerGram)}/g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Computed: ${CurrencyFormatter.format(item.computedValue)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.overrideValue != null) {
                Text(
                    "Override applied",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                "Paid: ${CurrencyFormatter.format(item.finalValue)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            if (thumbFile != null && thumbFile.exists()) {
                Spacer(Modifier.height(4.dp))
                AsyncImage(
                    model = thumbFile,
                    contentDescription = "Item photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(80.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            if (item.isSoldToSupplier) {
                val soldPrice = item.soldToSupplierPrice ?: 0.0
                val profit = item.profitFromSupplier ?: 0.0
                val perGram = if (item.weightGrams > 0) soldPrice / item.weightGrams else 0.0
                Text(
                    "Supplier paid: ${CurrencyFormatter.format(soldPrice)} " +
                        "(${CurrencyFormatter.format(perGram)}/g)" +
                        (item.soldToSupplierAt?.let { " • ${SoldDateSdf.format(Date(it))}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Profit: ${CurrencyFormatter.format(profit)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (profit >= 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error,
                )
                if (canUnmarkItem) {
                    TextButton(onClick = onUndoSold) {
                        Text("Mark as in stock")
                    }
                }
            } else {
                Button(
                    onClick = { showSellDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Sell to supplier")
                }
            }
        }
    }

    if (showSellDialog) {
        SellToSupplierDialog(
            weightGrams = item.weightGrams,
            paidValue = item.finalValue,
            onConfirm = { totalPrice ->
                showSellDialog = false
                onMarkSold(totalPrice)
            },
            onDismiss = { showSellDialog = false },
        )
    }
}

@Composable
private fun SellToSupplierDialog(
    weightGrams: Double,
    paidValue: Double,
    onConfirm: (totalPrice: Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var pricePerGramText by rememberSaveable { mutableStateOf("") }
    val pricePerGram = pricePerGramText.toDoubleOrNull()
    val totalPrice = pricePerGram?.let { it * weightGrams }
    val profit = totalPrice?.let { it - paidValue }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell to Supplier") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You paid ${CurrencyFormatter.format(paidValue)} for this item " +
                        "(${formatGramsLabel(weightGrams)}g). Enter the supplier's per-gram rate.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = pricePerGramText,
                    onValueChange = { pricePerGramText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Supplier rate (₱/g) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (totalPrice != null) {
                    Text(
                        "Supplier pays: ${CurrencyFormatter.format(totalPrice)}",
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
                onClick = { totalPrice?.let { onConfirm(it) } },
                enabled = totalPrice != null && totalPrice > 0,
            ) { Text("Mark Sold") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun formatGramsLabel(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)

@Composable
private fun RevertSoldDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revert sold items?") },
        text = {
            Text(
                "$count item${if (count == 1) "" else "s"} will move back to in-stock and " +
                    "be removed from supplier revenue and gold profit.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ),
            ) { Text("Revert Sold") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RevertConfirmDialog(
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revert Purchase?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This will permanently remove the purchase record. Provide a reason (required).",
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
            ) {
                Text("Revert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RevertTradeInConfirmDialog(
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revert Trade-in?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This is a trade-in. Reverting will also undo the linked sale and restore stock. Continue?",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
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
            ) {
                Text("Revert Trade-in")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
