package com.ykfj.inventory.ui.layaway

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.LayawayTransaction
import com.ykfj.inventory.ui.components.PaymentMethodPicker
import com.ykfj.inventory.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerLayawayDetailScreen(
    onNavigateUp: () -> Unit,
    onNavigateToProduct: (productId: String) -> Unit = {},
    viewModel: CustomerLayawayDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var addPaymentTarget by remember { mutableStateOf<LayawayEntryState?>(null) }
    var cancelTarget by remember { mutableStateOf<String?>(null) }
    var completeTarget by remember { mutableStateOf<String?>(null) }
    var revertCompletionTarget by remember { mutableStateOf<String?>(null) }
    var deletePaymentTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // txnId to layawayId

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it); viewModel.clearError() }
    }
    // state.success is now vestigial — VM emits through the global
    // SnackbarController instead.
    LaunchedEffect(state.success) {
        if (state.success != null) viewModel.clearSuccess()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(state.customerName.ifBlank { "Layaway" }) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Combined summary header
                if (state.entries.size > 1) {
                    item { CombinedSummaryCard(state) }
                }

                // Individual layaway entries
                items(items = state.entries, key = { it.record.id }) { entry ->
                    LayawayEntryCard(
                        entry = entry,
                        canManage = state.canManage,
                        canAdmin = state.canAdmin,
                        onAddPayment = { addPaymentTarget = entry },
                        onComplete = { completeTarget = entry.record.id },
                        onCancel = { cancelTarget = entry.record.id },
                        onRevertCompletion = { revertCompletionTarget = entry.record.id },
                        onDeletePayment = { txnId -> deletePaymentTarget = txnId to entry.record.id },
                        onViewProduct = { onNavigateToProduct(entry.record.productId) },
                    )
                }
            }
        }
    }

    // Dialogs
    addPaymentTarget?.let { entry ->
        AddSinglePaymentDialog(
            productName = entry.productName,
            remaining = entry.remaining,
            onConfirm = { amount, paymentMethod, notes ->
                viewModel.addPayment(entry.record.id, amount, paymentMethod, notes)
                addPaymentTarget = null
            },
            onDismiss = { addPaymentTarget = null },
        )
    }

    completeTarget?.let { layawayId ->
        AlertDialog(
            onDismissRequest = { completeTarget = null },
            title = { Text("Mark as Completed?") },
            text = { Text("This will mark this layaway as completed and release the product to inventory.") },
            confirmButton = {
                TextButton(onClick = { viewModel.completeLayaway(layawayId); completeTarget = null }) {
                    Text("Complete")
                }
            },
            dismissButton = { TextButton(onClick = { completeTarget = null }) { Text("Cancel") } },
        )
    }

    cancelTarget?.let { layawayId ->
        val forfeited = state.entries.find { it.record.id == layawayId }?.record?.totalPaid ?: 0.0
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("Cancel Layaway?") },
            text = {
                Text(
                    "All paid amounts (${CurrencyFormatter.format(forfeited)}) are forfeited with no refund. " +
                        "Reserved units will be returned to inventory.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.cancelLayaway(layawayId); cancelTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Cancel Layaway") }
            },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("Dismiss") } },
        )
    }

    deletePaymentTarget?.let { (txnId, layawayId) ->
        AlertDialog(
            onDismissRequest = { deletePaymentTarget = null },
            title = { Text("Delete Payment?") },
            text = { Text("This payment record will be removed and the total paid will be recalculated.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deletePayment(txnId, layawayId); deletePaymentTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletePaymentTarget = null }) { Text("Cancel") } },
        )
    }

    revertCompletionTarget?.let { layawayId ->
        AlertDialog(
            onDismissRequest = { revertCompletionTarget = null },
            title = { Text("Revert completion?") },
            text = {
                Text(
                    "This layaway will move back to ACTIVE, the auto-generated sale will be removed " +
                        "from the Sold Archive, and the product status will return to LAYAWAY.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revertCompletion(layawayId)
                    revertCompletionTarget = null
                }) { Text("Revert to Active") }
            },
            dismissButton = {
                TextButton(onClick = { revertCompletionTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CombinedSummaryCard(state: CustomerLayawayDetailUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Combined Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.bodyMedium)
                Text(CurrencyFormatter.format(state.totalAmount), fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Paid", style = MaterialTheme.typography.bodyMedium)
                Text(CurrencyFormatter.format(state.totalPaid), fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Remaining", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    CurrencyFormatter.format(state.totalRemaining),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LayawayEntryCard(
    entry: LayawayEntryState,
    canManage: Boolean,
    canAdmin: Boolean,
    onAddPayment: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onRevertCompletion: () -> Unit,
    onDeletePayment: (transactionId: String) -> Unit,
    onViewProduct: () -> Unit,
) {
    val dateSdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    val record = entry.record

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewProduct),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Product header — tap anywhere on the card to view product details.
            Text(
                entry.productName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )

            // Status / late-payment chips
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.isCompleted) {
                    AssistChip(
                        onClick = {},
                        label = { Text("COMPLETED", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    )
                }
                if (entry.wasPaidLate) {
                    AssistChip(
                        onClick = {},
                        label = { Text("LATE PAYMENT", style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    )
                }
            }

            Text(
                "Qty: ${record.quantity}  ·  Unit price: ${CurrencyFormatter.format(record.unitPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Layaway date: ${dateSdf.format(Date(record.createdAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.dueDate?.let { due ->
                Text(
                    "Due: ${dateSdf.format(Date(due))}${if (entry.isCurrentlyOverdue) " — OVERDUE" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (entry.isCurrentlyOverdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (entry.isCurrentlyOverdue) FontWeight.Bold else FontWeight.Normal,
                )
            }
            if (entry.isCompleted) {
                record.completionDate?.let { completed ->
                    Text(
                        "Completed: ${dateSdf.format(Date(completed))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Mini payment summary
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Paid: ${CurrencyFormatter.format(record.totalPaid)} / ${CurrencyFormatter.format(entry.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Left: ${CurrencyFormatter.format(entry.remaining)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (entry.isFullyPaid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                )
            }

            // Action buttons — only on ACTIVE entries
            if (entry.isActive && canManage) {
                Button(onClick = onAddPayment, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add Payment")
                }
            }
            if (entry.isActive && canAdmin) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Complete only enabled when fully paid
                    FilledTonalButton(
                        onClick = onComplete,
                        modifier = Modifier.weight(1f),
                        enabled = entry.isFullyPaid,
                    ) { Text("Mark Completed") }
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) { Text("Cancel", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }

            // Admin can revert a COMPLETED layaway back to ACTIVE
            if (entry.isCompleted && canAdmin) {
                Button(
                    onClick = onRevertCompletion,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                    ),
                ) { Text("Revert to Active") }
            }

            // Payment history
            if (entry.transactions.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    "Payments",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                entry.transactions.forEach { txn ->
                    // Only allow deleting payments while the layaway is ACTIVE.
                    TransactionItem(txn, canAdmin && entry.isActive, onDeletePayment)
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    txn: LayawayTransaction,
    canDelete: Boolean,
    onDelete: (transactionId: String) -> Unit,
) {
    val dateSdf = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(CurrencyFormatter.format(txn.amountPaid), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                dateSdf.format(Date(txn.paymentDate)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            txn.notes?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canDelete) {
            IconButton(onClick = { onDelete(txn.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete payment", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun AddSinglePaymentDialog(
    productName: String,
    remaining: Double,
    onConfirm: (amount: Double, paymentMethod: PaymentMethod, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    var notes by rememberSaveable { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val isValid = amount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$productName — Remaining: ${CurrencyFormatter.format(remaining)}", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Amount (₱)") },
                    isError = amountText.isNotBlank() && amount <= 0.0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Payment Method", style = MaterialTheme.typography.labelMedium)
                PaymentMethodPicker(
                    selected = selectedPaymentMethod,
                    onSelected = { selectedPaymentMethod = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(amount, selectedPaymentMethod, notes.ifBlank { null }) },
                enabled = isValid,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

