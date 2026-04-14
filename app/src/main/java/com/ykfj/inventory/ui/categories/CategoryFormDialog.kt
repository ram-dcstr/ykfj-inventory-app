package com.ykfj.inventory.ui.categories

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.ykfj.inventory.domain.model.Category

@Composable
fun CategoryFormDialog(
    editing: Category?,
    onDismiss: () -> Unit,
    onSubmit: (name: String) -> Unit,
) {
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    val canSubmit = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Add Category" else "Edit Category") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Necklace") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = { onSubmit(name.trim()) },
            ) {
                Text(if (editing == null) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
