package com.ykfj.inventory.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.data.local.db.enums.StockAdjustmentReason

/**
 * Writes off N units of a product for a reason (lost, stolen, miscount, …) instead
 * of deleting the whole product. Quantity is capped at [availableQty]; a free-text
 * note is required when the reason is "Other".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdjustStockDialog(
    availableQty: Int,
    onConfirm: (quantity: Int, reason: StockAdjustmentReason, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var qtyText by rememberSaveable { mutableStateOf("1") }
    var reason by rememberSaveable { mutableStateOf(StockAdjustmentReason.LOST) }
    var notes by rememberSaveable { mutableStateOf("") }
    var reasonExpanded by rememberSaveable { mutableStateOf(false) }

    val qty = qtyText.toIntOrNull()
    val qtyValid = qty != null && qty in 1..availableQty
    val notesRequired = reason == StockAdjustmentReason.OTHER
    val notesValid = !notesRequired || notes.isNotBlank()
    val canConfirm = qtyValid && notesValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Remove units from stock and record why. $availableQty in stock.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                    label = { Text("Units to remove") },
                    singleLine = true,
                    isError = qtyText.isNotEmpty() && !qtyValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = reasonExpanded,
                    onExpandedChange = { reasonExpanded = it },
                ) {
                    OutlinedTextField(
                        value = reason.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reason") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(reasonExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = reasonExpanded,
                        onDismissRequest = { reasonExpanded = false },
                    ) {
                        StockAdjustmentReason.entries.forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r.label) },
                                onClick = { reason = r; reasonExpanded = false },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (notesRequired) "Notes (required)" else "Notes (optional)") },
                    isError = notesRequired && notes.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (canConfirm) onConfirm(qty!!, reason, notes.trim().ifBlank { null }) },
                enabled = canConfirm,
            ) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
