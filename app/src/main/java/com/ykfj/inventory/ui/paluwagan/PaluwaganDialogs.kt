package com.ykfj.inventory.ui.paluwagan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.model.PaluwaganPayment
import com.ykfj.inventory.ui.customers.CustomerAutoSuggestViewModel
import com.ykfj.inventory.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Dialogs ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RecordPaymentDialog(
    slotRow: SlotRow,
    roundNumber: Int,
    defaultAmount: Double,
    totalSlots: Int,
    onConfirm: (amount: Double, paymentDate: Long, paymentMethod: PaymentMethod?, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountRaw by rememberSaveable { mutableStateOf(defaultAmount.toString()) }
    var paymentMethod by rememberSaveable { mutableStateOf<PaymentMethod?>(null) }
    var channelExpanded by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
    )
    val selectedDateMs = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
    val dateLabel = remember(selectedDateMs) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDateMs))
    }

    // The member's still-unpaid rounds from this round onward (roundNumber is their
    // earliest unpaid round). They may settle up to ALL of them in one payment — so a
    // member who missed everything can pay the full amount at the final round. A round
    // counts as unpaid if it has no row yet or its row is still UNPAID.
    val unpaidRoundsFromHere = (roundNumber..totalSlots).filter { r ->
        val p = slotRow.payments[r]
        p == null || p.status == PaluwaganPaymentStatus.UNPAID
    }
    val remainingRounds = unpaidRoundsFromHere.size.coerceAtLeast(1)
    val maxAmount = if (defaultAmount > 0) defaultAmount * remainingRounds else Double.MAX_VALUE

    val amount = amountRaw.toDoubleOrNull()
    val amountExceedsMax = amount != null && defaultAmount > 0 && amount > maxAmount
    // Paluwagan is paid in WHOLE contributions: the amount must be at least one full
    // contribution and an exact multiple of it (1×, 2×, 3× … to settle several rounds
    // at once). This blocks underpayments like ₱1,000 against a ₱2,000 contribution.
    val belowOneContribution = amount != null && defaultAmount > 0 && amount < defaultAmount
    val wholeRounds = if (amount != null && defaultAmount > 0)
        kotlin.math.round(amount / defaultAmount).toInt() else 0
    val isWholeContribution = amount == null || defaultAmount <= 0 ||
        (wholeRounds >= 1 && kotlin.math.abs(amount - wholeRounds * defaultAmount) < 0.01)
    val amountError: String? = when {
        amount == null || amount <= 0 -> null
        belowOneContribution ->
            "Must pay at least one full contribution (${CurrencyFormatter.format(defaultAmount)})"
        !isWholeContribution ->
            "Must be a multiple of the ${CurrencyFormatter.format(defaultAmount)} contribution"
        amountExceedsMax ->
            "Max is ${CurrencyFormatter.format(maxAmount)} ($remainingRounds rounds remaining)"
        else -> null
    }
    val isValid = amount != null && amount > 0 && amountError == null

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Round $roundNumber — ${slotRow.customerName} (slot #${slotRow.position})",
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Amount
                OutlinedTextField(
                    value = amountRaw,
                    onValueChange = { amountRaw = it },
                    label = { Text("Amount Paid (₱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                // Multi-round payment hint. We settle the member's earliest unpaid
                // rounds first, so list exactly which rounds this amount covers
                // (they may be missed past rounds, the current round, and/or future
                // pre-paid rounds — not necessarily a contiguous "will be PRE-PAID" range).
                val enteredAmount = amountRaw.toDoubleOrNull() ?: 0.0
                val roundsCovered = if (defaultAmount > 0) (enteredAmount / defaultAmount).toInt().coerceAtLeast(0) else 0
                if (roundsCovered > 1) {
                    val coveredRounds = unpaidRoundsFromHere.take(roundsCovered)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF1565C0).copy(alpha = 0.12f), shape = MaterialTheme.shapes.small)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                "Covers ${coveredRounds.size} rounds",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF1565C0),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(
                            "Settles rounds ${coveredRounds.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Payment date
                OutlinedTextField(
                    value = dateLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                )

                // Payment channel
                ExposedDropdownMenuBox(
                    expanded = channelExpanded,
                    onExpandedChange = { channelExpanded = it },
                ) {
                    OutlinedTextField(
                        value = paymentMethod?.label ?: "Select channel",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment Channel") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(channelExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = channelExpanded,
                        onDismissRequest = { channelExpanded = false },
                    ) {
                        PaymentMethod.entries.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.label) },
                                onClick = { paymentMethod = method; channelExpanded = false },
                            )
                        }
                    }
                }

                // Notes
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Auto-status hint
                Text(
                    "Status is set automatically based on whether payment is before or after the scheduled collection date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) onConfirm(
                        amount!!,
                        selectedDateMs,
                        paymentMethod,
                        notes.trim().ifBlank { null },
                    )
                },
                enabled = isValid,
            ) { Text("Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
internal fun AddMemberDialog(
    currentMemberCount: Int,
    totalSlots: Int,
    onConfirm: (customerIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
    autoSuggestViewModel: CustomerAutoSuggestViewModel = hiltViewModel(),
) {
    val suggestions by autoSuggestViewModel.suggestions.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val pendingCustomers = remember { mutableStateListOf<Customer>() }
    val totalSelected = currentMemberCount + pendingCustomers.size
    val canAddMore = totalSelected < totalSlots

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Header ────────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Add Members", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (canAddMore)
                                "$totalSelected / $totalSlots slots filled"
                            else
                                "$totalSelected / $totalSlots  •  All slots filled",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canAddMore)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            onClick = { onConfirm(pendingCustomers.map { it.id }) },
                            enabled = pendingCustomers.isNotEmpty(),
                        ) { Text("Save") }
                    }
                }

                HorizontalDivider()

                // ── Scrollable body ───────────────────────────────────────
                LazyColumn(modifier = Modifier.weight(1f)) {

                    // Queued selections
                    if (pendingCustomers.isNotEmpty()) {
                        item {
                            Text(
                                "Queued (${pendingCustomers.size})",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                            )
                        }
                        itemsIndexed(pendingCustomers) { i, customer ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${currentMemberCount + i + 1}.  ${customer.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { pendingCustomers.removeAt(i) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    // Search field
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { q ->
                                query = q
                                autoSuggestViewModel.onQueryChange(q)
                            },
                            label = { Text("Search customer") },
                            placeholder = { Text("Type a name…") },
                            singleLine = true,
                            enabled = canAddMore,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }

                    // Results
                    when {
                        !canAddMore -> item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "All $totalSlots slots are filled.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }

                        query.isBlank() -> item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Start typing to search customers",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        suggestions.isEmpty() -> item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No customers found for \"$query\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        else -> items(suggestions) { customer ->
                            val addedCount = pendingCustomers.count { it.id == customer.id }
                            CustomerPickerRow(
                                customer = customer,
                                addedCount = addedCount,
                                onClick = { pendingCustomers.add(customer) },
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/**
 * Pasalo dialog: admin picks a replacement customer for a slot.
 * Tap a customer row to stage them, then confirm with the "Pasalo" button.
 */
@Composable
internal fun EditSlotCustomerDialog(
    slotRow: SlotRow,
    onConfirm: (newCustomerId: String) -> Unit,
    onDismiss: () -> Unit,
    autoSuggestViewModel: CustomerAutoSuggestViewModel = hiltViewModel(),
) {
    val suggestions by autoSuggestViewModel.suggestions.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var staged by remember { mutableStateOf<Customer?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Pasalo — Slot #${slotRow.position}", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Current: ${slotRow.customerName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(
                            onClick = { staged?.let { onConfirm(it.id) } },
                            enabled = staged != null,
                        ) { Text("Pasalo") }
                    }
                }

                // Staged selection preview
                staged?.let { customer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Replace with: ${customer.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "Tap another customer to change selection",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { staged = null },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear selection",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                HorizontalDivider()

                // Search + results
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { q ->
                                query = q
                                autoSuggestViewModel.onQueryChange(q)
                            },
                            label = { Text("Search replacement customer") },
                            placeholder = { Text("Type a name…") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                        )
                    }

                    when {
                        query.isBlank() -> item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "Search for the customer who will take over this slot",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        suggestions.isEmpty() -> item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No customers found for \"$query\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> items(suggestions) { customer ->
                            val isSelected = staged?.id == customer.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else
                                            Color.Transparent,
                                    )
                                    .clickable { staged = customer }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        customer.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                    customer.mobile?.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                shape = MaterialTheme.shapes.small,
                                            )
                                            .padding(horizontal = 10.dp, vertical = 4.dp),
                                    ) {
                                        Text(
                                            "Selected",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

// ── Member payment history dialog ─────────────────────────────────────────────

@Composable
internal fun MemberPaymentHistoryDialog(
    slotRow: SlotRow,
    slotBadge: String?,
    currentRound: Int,
    isAdmin: Boolean,
    isAdminOrManager: Boolean,
    groupStatus: PaluwaganGroupStatus?,
    onEditPayment: (PaluwaganPayment) -> Unit,
    onRecordPayment: (roundNumber: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.82f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Payment History", style = MaterialTheme.typography.titleLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                slotRow.customerName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (slotBadge != null) {
                                SlotBadge(slotBadge)
                            }
                        }
                        if (slotRow.originalCustomerName != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    "was ${slotRow.originalCustomerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontStyle = FontStyle.Italic,
                                )
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Payment rows (all rounds 1..currentRound)
                if (currentRound == 0) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No rounds started yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items((1..currentRound).toList()) { round ->
                            val payment = slotRow.payments[round]

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Round + date + channel
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Round $round",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (payment?.paymentDate != null) {
                                        Text(
                                            buildString {
                                                append(dateFmt.format(Date(payment.paymentDate)))
                                                payment.paymentMethod?.let { append("  •  ${it.label}") }
                                                payment.amountPaid.let { append("  •  ₱%.2f".format(it)) }
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    if (!payment?.notes.isNullOrBlank()) {
                                        Text(
                                            payment!!.notes!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }

                                // Status badge or Record button for unpaid past rounds
                                val canRecordThisRound = isAdminOrManager &&
                                    groupStatus == PaluwaganGroupStatus.ACTIVE &&
                                    payment?.status == PaluwaganPaymentStatus.UNPAID
                                PaymentBadgeOrButton(
                                    status = payment?.status,
                                    canRecord = canRecordThisRound,
                                    onRecord = { onRecordPayment(round) },
                                )

                                // Admin edit button (only for recorded payments)
                                if (isAdmin && payment != null &&
                                    payment.status != PaluwaganPaymentStatus.UNPAID
                                ) {
                                    IconButton(
                                        onClick = { onEditPayment(payment) },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit payment",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }

                            if (round < currentRound) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp))
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// ── Edit recorded payment dialog (admin only) ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditPaymentDialog(
    payment: PaluwaganPayment,
    contributionAmount: Double,
    onConfirm: (amount: Double, paymentDate: Long, paymentMethod: PaymentMethod?, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountRaw by rememberSaveable { mutableStateOf(payment.amountPaid.toString()) }
    var paymentMethod by rememberSaveable { mutableStateOf(payment.paymentMethod) }
    var channelExpanded by remember { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf(payment.notes ?: "") }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = payment.paymentDate ?: System.currentTimeMillis(),
    )
    val selectedDateMs = datePickerState.selectedDateMillis ?: (payment.paymentDate ?: System.currentTimeMillis())
    val dateLabel = remember(selectedDateMs) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDateMs))
    }

    val amount = amountRaw.toDoubleOrNull()
    // A recorded round can't be corrected to less than one full contribution.
    val belowOneContribution = amount != null && contributionAmount > 0 && amount < contributionAmount
    val amountError: String? = when {
        amount == null || amount <= 0 -> null
        belowOneContribution ->
            "Must be at least one full contribution (${CurrencyFormatter.format(contributionAmount)})"
        else -> null
    }
    val isValid = amount != null && amount > 0 && amountError == null

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Round ${payment.roundNumber}  •  Editing will recompute PAID / LATE status",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                OutlinedTextField(
                    value = amountRaw,
                    onValueChange = { amountRaw = it },
                    label = { Text("Amount Paid (₱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = amountError?.let {
                        { Text(it, color = MaterialTheme.colorScheme.error) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = dateLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Payment Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                )

                ExposedDropdownMenuBox(
                    expanded = channelExpanded,
                    onExpandedChange = { channelExpanded = it },
                ) {
                    OutlinedTextField(
                        value = paymentMethod?.label ?: "Select channel",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payment Channel") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(channelExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = channelExpanded,
                        onDismissRequest = { channelExpanded = false },
                    ) {
                        PaymentMethod.entries.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.label) },
                                onClick = { paymentMethod = method; channelExpanded = false },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) onConfirm(amount!!, selectedDateMs, paymentMethod, notes.trim().ifBlank { null })
                },
                enabled = isValid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ── Collect Pot dialog ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectPotDialog(
    slotRow: SlotRow,
    onConfirm: (date: Long, payoutChannel: PaymentMethod) -> Unit,
    onDismiss: () -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var channelExpanded by remember { mutableStateOf(false) }
    var payoutChannel by remember { mutableStateOf(PaymentMethod.CASH) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = System.currentTimeMillis(),
    )
    val selectedDateMs = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
    val dateLabel = remember(selectedDateMs) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDateMs))
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = { TextButton(onClick = { showDatePicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = datePickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Pot Collection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Slot #${slotRow.position} — ${slotRow.customerName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Record the actual date and channel this member received the pot money.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = dateLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Collection Date") },
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarToday, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                )

                // Payout channel — how the pot money was handed over.
                ExposedDropdownMenuBox(
                    expanded = channelExpanded,
                    onExpandedChange = { channelExpanded = it },
                ) {
                    OutlinedTextField(
                        value = payoutChannel.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Payout Channel") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(channelExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = channelExpanded,
                        onDismissRequest = { channelExpanded = false },
                    ) {
                        PaymentMethod.entries.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method.label) },
                                onClick = { payoutChannel = method; channelExpanded = false },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedDateMs, payoutChannel) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CustomerPickerRow(
    customer: Customer,
    addedCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                customer.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            customer.mobile?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (addedCount > 0) {
            Box(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "×$addedCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
