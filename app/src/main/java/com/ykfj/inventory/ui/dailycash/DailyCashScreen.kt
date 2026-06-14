package com.ykfj.inventory.ui.dailycash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.domain.model.CashMovement
import com.ykfj.inventory.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DailyCashScreen(viewModel: DailyCashViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedDay by viewModel.selectedDay.collectAsStateWithLifecycle()
    val dateLabelSdf = remember { SimpleDateFormat("EEEE, MMM d, yyyy", Locale.US) }

    // Each time the screen is opened, snap the date filter back to today — a
    // previously-browsed day shouldn't linger after navigating away and back.
    LaunchedEffect(Unit) { viewModel.today() }

    var showEditChangeFloat by rememberSaveable { mutableStateOf(false) }
    var showEditPurchaseFloat by rememberSaveable { mutableStateOf(false) }
    var showAddExpense by rememberSaveable { mutableStateOf(false) }
    var showAddAdjustment by rememberSaveable { mutableStateOf(false) }

    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Date navigation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = viewModel::previousDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = dateLabelSdf.format(Date(selectedDay)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = viewModel::today) {
                    Text("Today", style = MaterialTheme.typography.labelSmall)
                }
            }
            IconButton(onClick = viewModel::nextDay) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
        }

        // ── Cash section ───────────────────────────────────────────────────
        SectionCard(title = "Cash (in store)") {
            EditableRow(
                label = "Change float",
                amount = state.changeFloat,
                canEdit = state.isAdminOrManager,
                onEdit = { showEditChangeFloat = true },
            )
            EditableRow(
                label = "Purchase float",
                amount = state.purchaseFloat,
                canEdit = state.isAdminOrManager,
                onEdit = { showEditPurchaseFloat = true },
            )
            AmountRow("Cash sales", state.cashSales)
            AmountRow("Cash layaway payments", state.cashLayawayPayments)
            AmountRow("Paluwagan contributions", state.cashPaluwaganContributions)
            AmountRow(
                label = "Gold purchases (out)",
                amount = -state.goldPurchasesTotal,
                negativeTint = true,
            )

            // Expenses
            if (state.expenses.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Expenses", style = MaterialTheme.typography.labelLarge)
                state.expenses.forEach { exp ->
                    MovementRow(
                        movement = exp,
                        canDelete = state.isAdminOrManager,
                        onDelete = { viewModel.deleteMovement(exp.id) },
                    )
                }
            }
            if (state.isAdminOrManager) {
                OutlinedButton(
                    onClick = { showAddExpense = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Add expense")
                }
            }

            // Adjustments (admin only)
            if (state.adjustments.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Adjustments", style = MaterialTheme.typography.labelLarge)
                state.adjustments.forEach { adj ->
                    MovementRow(
                        movement = adj,
                        canDelete = state.isAdmin,
                        onDelete = { viewModel.deleteMovement(adj.id) },
                    )
                }
            }
            if (state.isAdmin) {
                OutlinedButton(
                    onClick = { showAddAdjustment = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text(" Add adjustment")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            BalanceRow("Cash balance", state.cashBalance, big = true)
        }

        // ── GCash section ──────────────────────────────────────────────────
        SectionCard(title = "GCash") {
            AmountRow("GCash sales", state.gcashSales)
            AmountRow("GCash layaway payments", state.gcashLayawayPayments)
            AmountRow("Paluwagan contributions", state.gcashPaluwaganContributions)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            BalanceRow("GCash balance", state.gcashBalance, big = true)
        }

        // ── Online Banking section ─────────────────────────────────────────
        SectionCard(title = "Online Banking") {
            AmountRow("Online Banking sales", state.onlineBankingSales)
            AmountRow("Online Banking layaway payments", state.onlineBankingLayawayPayments)
            AmountRow("Paluwagan contributions", state.onlineBankingPaluwaganContributions)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            BalanceRow("Online Banking balance", state.onlineBankingBalance, big = true)
        }

        // ── Other (only shown if there's activity, keeps day view tight) ───
        if (state.otherSales != 0.0 || state.otherLayawayPayments != 0.0 ||
            state.otherPaluwaganContributions != 0.0
        ) {
            SectionCard(title = "Other payment methods") {
                AmountRow("Other sales", state.otherSales)
                AmountRow("Other layaway payments", state.otherLayawayPayments)
                AmountRow("Paluwagan contributions", state.otherPaluwaganContributions)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                BalanceRow("Other balance", state.otherBalance, big = true)
            }
        }

        // ── Grand total ────────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Total collected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    CurrencyFormatter.format(state.totalCollected),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showEditChangeFloat) {
        AmountInputDialog(
            title = "Change float",
            initialAmount = state.changeFloat,
            onConfirm = { viewModel.editChangeFloat(it); showEditChangeFloat = false },
            onDismiss = { showEditChangeFloat = false },
        )
    }
    if (showEditPurchaseFloat) {
        AmountInputDialog(
            title = "Purchase float",
            initialAmount = state.purchaseFloat,
            onConfirm = { viewModel.setPurchaseFloat(it); showEditPurchaseFloat = false },
            onDismiss = { showEditPurchaseFloat = false },
        )
    }
    if (showAddExpense) {
        AmountWithNotesDialog(
            title = "Add expense",
            amountLabel = "Amount (₱)",
            notesLabel = "What is this for? *",
            requireNotes = true,
            onConfirm = { amount, notes -> viewModel.addExpense(amount, notes); showAddExpense = false },
            onDismiss = { showAddExpense = false },
        )
    }
    if (showAddAdjustment) {
        AmountWithNotesDialog(
            title = "Add adjustment",
            amountLabel = "Amount (₱, use negative for out)",
            notesLabel = "Reason *",
            requireNotes = true,
            allowNegative = true,
            onConfirm = { amount, notes -> viewModel.addAdjustment(amount, notes); showAddAdjustment = false },
            onDismiss = { showAddAdjustment = false },
        )
    }
}

// ── Section + row primitives ─────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun AmountRow(label: String, amount: Double, negativeTint: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = CurrencyFormatter.format(amount),
            style = MaterialTheme.typography.bodyMedium,
            color = if (negativeTint || amount < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EditableRow(
    label: String,
    amount: Double,
    canEdit: Boolean,
    onEdit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (canEdit) it.clickable(onClick = onEdit) else it },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(CurrencyFormatter.format(amount), style = MaterialTheme.typography.bodyMedium)
            if (canEdit) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit $label")
                }
            }
        }
    }
}

@Composable
private fun BalanceRow(label: String, amount: Double, big: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = if (big) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            CurrencyFormatter.format(amount),
            style = if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (amount < 0) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun MovementRow(
    movement: CashMovement,
    canDelete: Boolean,
    onDelete: () -> Unit,
) {
    var expanded by rememberSaveable(movement.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = movement.notes ?: "(no notes)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                CurrencyFormatter.format(movement.amount),
                style = MaterialTheme.typography.bodyMedium,
                color = if (movement.amount < 0) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recorded ${SimpleDateFormat("h:mm a", Locale.US).format(Date(movement.recordedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun AmountInputDialog(
    title: String,
    initialAmount: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by rememberSaveable(initialAmount) {
        mutableStateOf(if (initialAmount > 0) "%.2f".format(initialAmount) else "")
    }
    val amount = field.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (₱)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let(onConfirm) },
                enabled = amount != null && amount >= 0,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AmountWithNotesDialog(
    title: String,
    amountLabel: String,
    notesLabel: String,
    requireNotes: Boolean,
    allowNegative: Boolean = false,
    onConfirm: (amount: Double, notes: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountField by rememberSaveable { mutableStateOf("") }
    var notesField by rememberSaveable { mutableStateOf("") }
    val amount = amountField.toDoubleOrNull()
    val notesOk = !requireNotes || notesField.isNotBlank()
    val amountOk = amount != null && (allowNegative || amount > 0) && amount != 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amountField,
                    onValueChange = {
                        amountField = it.filter { c -> c.isDigit() || c == '.' || (allowNegative && c == '-') }
                    },
                    label = { Text(amountLabel) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notesField,
                    onValueChange = { notesField = it },
                    label = { Text(notesLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { amount?.let { onConfirm(it, notesField.trim()) } },
                enabled = amountOk && notesOk,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// Re-export ColumnScope so SectionCard's content lambda compiles without a separate import.
private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope
