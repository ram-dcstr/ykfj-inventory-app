package com.ykfj.inventory.ui.metalrates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.domain.model.MetalRate

@Composable
fun MetalRateFormDialog(
    editing: MetalRate?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, pricePerGram: Double) -> Unit,
) {
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var priceText by rememberSaveable(editing?.id) {
        mutableStateOf(editing?.pricePerGram?.toString().orEmpty())
    }

    val price = remember(priceText) { priceText.toDoubleOrNull() }
    val canSubmit = name.isNotBlank() && price != null && price > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Add Metal Rate" else "Edit Metal Rate") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("e.g. 18K Saudi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Price per gram (₱)") },
                    placeholder = { Text("e.g. 3200.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = { onSubmit(name.trim(), price ?: 0.0) },
            ) {
                Text(if (editing == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
