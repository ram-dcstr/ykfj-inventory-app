package com.ykfj.inventory.ui.layaway

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.LayawayTransaction
import com.ykfj.inventory.domain.usecase.layaway.SplitLayawayPaymentUseCase
import com.ykfj.inventory.ui.components.PaymentMethodPicker
import com.ykfj.inventory.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayawayDetailScreen(
    onNavigateUp: () -> Unit,
    viewModel: LayawayDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showAddPayment by remember { mutableStateOf(false) }
    var showSplitPayment by remember { mutableStateOf(false) }
    var showEditLayaway by remember { mutableStateOf(false) }
    var showConfirmComplete by remember { mutableStateOf(false) }
    var showConfirmCancel by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it); viewModel.clearError() }
    }
    LaunchedEffect(state.success) {
        state.success?.let { snackbarHost.showSnackbar(it); viewModel.clearSuccess() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(if (state.record == null) "Layaway" else state.customerName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.canEdit) {
                        IconButton(onClick = { showEditLayaway = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit layaway")
                        }
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { LayawayHeaderCard(state) }
                item { LayawaySummaryCard(state) }

                if (state.canAddPayment) {
                    item { PaymentActionRow(
                        hasOtherLayaways = state.otherActiveLayaways.isNotEmpty(),
                        onAddPayment = { showAddPayment = true },
                        onSplitPayment = { showSplitPayment = true },
                    ) }
                }

                if (state.canEdit && state.record?.status == LayawayStatus.ACTIVE) {
                    item { AdminActionRow(
                        onComplete = { showConfirmComplete = true },
                        onCancel = { showConfirmCancel = true },
                    ) }
                }

                if (state.transactions.isNotEmpty()) {
                    item {
                        Text(
                            "Payment History",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(items = state.transactions, key = { it.id }) { txn ->
                        TransactionRow(
                            transaction = txn,
                            canDelete = state.canEdit,
                            onDelete = { deleteTarget = txn.id },
                        )
                    }
                }
            }
        }
    }

    if (showAddPayment) {
        AddPaymentDialog(
            remaining = state.record?.let { (it.unitPrice * it.quantity) - it.totalPaid } ?: 0.0,
            onConfirm = { amount, paymentMethod, notes ->
                viewModel.addPayment(amount, paymentMethod, notes)
                showAddPayment = false
            },
            onDismiss = { showAddPayment = false },
        )
    }

    if (showSplitPayment && state.record != null) {
        SplitPaymentDialog(
            currentLayaway = LayawayRow(
                id = state.record!!.id,
                productId = state.record!!.productId,
                productName = state.productName,
                customerId = state.record!!.customerId,
                customerName = state.customerName,
                quantity = state.record!!.quantity,
                unitPrice = state.record!!.unitPrice,
                totalPaid = state.record!!.totalPaid,
                dueDate = state.record!!.dueDate,
                createdAt = state.record!!.createdAt,
                status = state.record!!.status,
                completionDate = state.record!!.completionDate,
            ),
            otherLayaways = state.otherActiveLayaways,
            onConfirm = { allocations, paymentMethod ->
                viewModel.splitPayment(allocations, paymentMethod)
                showSplitPayment = false
            },
            onDismiss = { showSplitPayment = false },
        )
    }

    if (showEditLayaway && state.record != null) {
        EditLayawayDialog(
            record = state.record!!,
            customerName = state.customerName,
            onConfirm = { customerId, qty, price, due ->
                viewModel.updateLayaway(customerId, qty, price, due)
                showEditLayaway = false
            },
            onDismiss = { showEditLayaway = false },
        )
    }

    if (showConfirmComplete) {
        AlertDialog(
            onDismissRequest = { showConfirmComplete = false },
            title = { Text("Mark as Completed?") },
            text = { Text("This will mark the layaway as completed and release the product back to inventory. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { viewModel.completeLayaway(); showConfirmComplete = false }) {
                    Text("Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmComplete = false }) { Text("Cancel") }
            },
        )
    }

    if (showConfirmCancel) {
        AlertDialog(
            onDismissRequest = { showConfirmCancel = false },
            title = { Text("Cancel Layaway?") },
            text = {
                val forfeited = state.record?.totalPaid ?: 0.0
                Text(
                    "This will cancel the layaway. All paid amounts " +
                        "(${CurrencyFormatter.format(forfeited)}) are forfeited with no refund. " +
                        "The reserved units will be returned to inventory.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.cancelLayaway(); showConfirmCancel = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Cancel Layaway") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmCancel = false }) { Text("Dismiss") }
            },
        )
    }

    deleteTarget?.let { txnId ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Payment?") },
            text = { Text("This payment record will be permanently removed and the total paid amount will be recalculated.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deletePayment(txnId); deleteTarget = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LayawayHeaderCard(state: LayawayDetailUiState) {
    val dateSdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }
    val record = state.record ?: return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(state.productName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Customer: ${state.customerName}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Qty: ${record.quantity}  ·  Unit price: ${CurrencyFormatter.format(record.unitPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            record.dueDate?.let { due ->
                val now = System.currentTimeMillis()
                val overdue = now > due
                Text(
                    "Due: ${dateSdf.format(Date(due))}${if (overdue) " — OVERDUE" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (overdue) FontWeight.Bold else FontWeight.Normal,
                )
            }
            val statusColor = when (record.status) {
                LayawayStatus.ACTIVE -> MaterialTheme.colorScheme.primary
                LayawayStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
                LayawayStatus.CANCELLED -> MaterialTheme.colorScheme.error
            }
            Text(
                text = record.status.name,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LayawaySummaryCard(state: LayawayDetailUiState) {
    val record = state.record ?: return
    val total = record.unitPrice * record.quantity
    val remaining = total - record.totalPaid

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Payment Summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.bodyMedium)
                Text(CurrencyFormatter.format(total), fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Paid", style = MaterialTheme.typography.bodyMedium)
                Text(CurrencyFormatter.format(record.totalPaid), fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Remaining", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    CurrencyFormatter.format(remaining),
                    fontWeight = FontWeight.Bold,
                    color = if (remaining <= 0) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.primary,
                )
            }
            if (record.status == LayawayStatus.CANCELLED && record.forfeitedAmount != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Forfeited", style = MaterialTheme.typography.bodySmall)
                    Text(
                        CurrencyFormatter.format(record.forfeitedAmount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentActionRow(
    hasOtherLayaways: Boolean,
    onAddPayment: () -> Unit,
    onSplitPayment: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onAddPayment, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add Payment")
        }
        if (hasOtherLayaways) {
            FilledTonalButton(onClick = onSplitPayment, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Split Payment")
            }
        }
    }
}

@Composable
private fun AdminActionRow(onComplete: () -> Unit, onCancel: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(onClick = onComplete, modifier = Modifier.weight(1f)) {
            Text("Mark Completed")
        }
        Button(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        ) {
            Text("Cancel Layaway", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: LayawayTransaction,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    val dateSdf = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    CurrencyFormatter.format(transaction.amountPaid),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    dateSdf.format(Date(transaction.paymentDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                transaction.notes?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete payment", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ─── Dialogs ─────────────────────────────────────────────────────────────────

@Composable
private fun AddPaymentDialog(
    remaining: Double,
    onConfirm: (amount: Double, paymentMethod: PaymentMethod, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }
    var notes by rememberSaveable { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull() ?: 0.0
    val isValid = amount > 0.0 && amount <= remaining + 0.01 // allow tiny overpayment to auto-complete

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Remaining balance: ${CurrencyFormatter.format(remaining)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
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

@Composable
private fun SplitPaymentDialog(
    currentLayaway: LayawayRow,
    otherLayaways: List<LayawayRow>,
    onConfirm: (List<SplitLayawayPaymentUseCase.Allocation>, PaymentMethod) -> Unit,
    onDismiss: () -> Unit,
) {
    val allLayaways = listOf(currentLayaway) + otherLayaways
    val amounts = remember { mutableStateOf(allLayaways.associateWith { "" }) }
    var selectedPaymentMethod by rememberSaveable { mutableStateOf(PaymentMethod.CASH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Allocate payment amounts across ${allLayaways.size} active layaways.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                allLayaways.forEach { row ->
                    val remaining = row.remaining
                    OutlinedTextField(
                        value = amounts.value[row] ?: "",
                        onValueChange = { new ->
                            amounts.value = amounts.value.toMutableMap().also { it[row] = new.filter { c -> c.isDigit() || c == '.' } }
                        },
                        label = { Text("${row.productName} (rem. ${CurrencyFormatter.format(remaining)})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                Text("Payment Method", style = MaterialTheme.typography.labelMedium)
                PaymentMethodPicker(
                    selected = selectedPaymentMethod,
                    onSelected = { selectedPaymentMethod = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            val allocations = allLayaways.mapNotNull { row ->
                val a = amounts.value[row]?.toDoubleOrNull() ?: 0.0
                if (a > 0.0) SplitLayawayPaymentUseCase.Allocation(row.id, a) else null
            }
            TextButton(
                onClick = { if (allocations.isNotEmpty()) onConfirm(allocations, selectedPaymentMethod) },
                enabled = allocations.isNotEmpty(),
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLayawayDialog(
    record: com.ykfj.inventory.domain.model.LayawayRecord,
    customerName: String,
    onConfirm: (customerId: String, quantity: Int, unitPrice: Double, dueDate: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var qtyText by rememberSaveable { mutableStateOf(record.quantity.toString()) }
    var priceText by rememberSaveable { mutableStateOf(record.unitPrice.toString()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dueDate by rememberSaveable { mutableStateOf(record.dueDate) }
    val dateSdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.US) }

    val qty = qtyText.toIntOrNull() ?: 0
    val price = priceText.toDoubleOrNull() ?: 0.0
    val isValid = qty > 0 && price > 0.0

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate)
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { dueDate = datePickerState.selectedDateMillis; showDatePicker = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { androidx.compose.material3.DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Layaway") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Customer: $customerName", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                    label = { Text("Quantity") },
                    isError = qtyText.isNotBlank() && qty < 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Unit price (₱)") },
                    isError = priceText.isNotBlank() && price <= 0.0,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = dueDate?.let { "Due: ${dateSdf.format(Date(it))}" } ?: "No due date",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row {
                        TextButton(onClick = { showDatePicker = true }) { Text("Set") }
                        if (dueDate != null) {
                            TextButton(onClick = { dueDate = null }) { Text("Clear") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(record.customerId, qty, price, dueDate) },
                enabled = isValid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
