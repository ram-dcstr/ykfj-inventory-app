package com.ykfj.inventory.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.ui.customers.CustomerAutoSuggest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayawayDialog(
    availableQty: Int,
    defaultPrice: Double?,
    initialCustomer: Customer? = null,
    onConfirm: (customerId: String, qty: Int, unitPrice: Double, dueDate: Long?, downpayment: Double?) -> Unit,
    onDismiss: () -> Unit,
    onNavigateToCustomers: () -> Unit,
) {
    var selectedCustomer by remember { mutableStateOf(initialCustomer) }

    LaunchedEffect(initialCustomer) {
        if (initialCustomer != null) selectedCustomer = initialCustomer
    }
    var qtyText by rememberSaveable { mutableStateOf("1") }
    var priceText by rememberSaveable { mutableStateOf(defaultPrice?.let { "%.2f".format(it) }.orEmpty()) }
    var downpaymentText by rememberSaveable { mutableStateOf("") }
    var dueDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val qty = qtyText.toIntOrNull() ?: 0
    val qtyError = qty < 1 || qty > availableQty
    val unitPrice = priceText.toDoubleOrNull() ?: 0.0
    val downpayment = downpaymentText.toDoubleOrNull()?.takeIf { it > 0 }
    val total = unitPrice * qty.coerceAtLeast(1)
    val downpaymentError = downpayment != null && downpayment > total
    val isValid = selectedCustomer != null && !qtyError && unitPrice > 0 && !downpaymentError
    val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = dueDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { dueDate = pickerState.selectedDateMillis; showDatePicker = false }) {
                    Text("OK")
                }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark as Layaway") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CustomerAutoSuggest(
                    selectedCustomer = selectedCustomer,
                    onCustomerSelected = { selectedCustomer = it },
                )
                TextButton(
                    onClick = { onNavigateToCustomers() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = "Customer not in directory? Add in Customers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                        label = { Text("Qty (max $availableQty)") },
                        isError = qtyText.isNotBlank() && qtyError,
                        supportingText = if (qtyText.isNotBlank() && qtyError) {
                            { Text("1–$availableQty") }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Price / unit (₱)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }

                // Downpayment (optional)
                OutlinedTextField(
                    value = downpaymentText,
                    onValueChange = { downpaymentText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Downpayment (optional, ₱)") },
                    isError = downpaymentError,
                    supportingText = if (downpaymentError) {
                        { Text("Cannot exceed total ${"%,.2f".format(total)}") }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Due date (optional)
                OutlinedTextField(
                    value = dueDate?.let { dateFmt.format(Date(it)) } ?: "No due date",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Due Date (optional)") },
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text("Set") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (isValid) {
                        onConfirm(selectedCustomer!!.id, qty, unitPrice, dueDate, downpayment)
                    }
                },
                enabled = isValid,
            ) { Text("Confirm Layaway") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
