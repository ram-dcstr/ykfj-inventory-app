package com.ykfj.inventory.ui.damaged

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DamagedScreen(
    onNavigateToProduct: (productId: String) -> Unit = {},
    viewModel: DamagedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var revertTarget by remember { mutableStateOf<DamagedRecordRow?>(null) }
    var meltTarget by remember { mutableStateOf<DamagedRecordRow?>(null) }
    var revertMeltTarget by remember { mutableStateOf<DamagedRecordRow?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.filter == DamagedFilter.Active,
                    onClick = { viewModel.setFilter(DamagedFilter.Active) },
                    label = { Text("Active", style = MaterialTheme.typography.labelMedium) },
                )
                FilterChip(
                    selected = state.filter == DamagedFilter.Melted,
                    onClick = { viewModel.setFilter(DamagedFilter.Melted) },
                    label = { Text("Melted", style = MaterialTheme.typography.labelMedium) },
                )
            }
            HorizontalDivider()

            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.records.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.filter == DamagedFilter.Melted) "No melted items"
                        else "No damaged items",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.records, key = { it.id }) { row ->
                        DamagedRecordCard(
                            row = row,
                            canRevert = state.canRevert,
                            canRevertMelt = state.canRevertMelt,
                            onRevert = { revertTarget = row },
                            onMelt = { meltTarget = row },
                            onRevertMelt = { revertMeltTarget = row },
                            onViewProduct = { onNavigateToProduct(row.productId) },
                        )
                    }
                }
            }
        }
    }

    revertTarget?.let { target ->
        RevertConfirmDialog(
            productName = target.productName,
            onConfirm = { reason ->
                viewModel.revert(target.id, reason)
                revertTarget = null
            },
            onDismiss = { revertTarget = null },
        )
    }

    meltTarget?.let { target ->
        MeltConfirmDialog(
            productName = target.productName,
            onConfirm = { notes ->
                viewModel.melt(target.id, notes)
                meltTarget = null
            },
            onDismiss = { meltTarget = null },
        )
    }

    revertMeltTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { revertMeltTarget = null },
            title = { Text("Revert melt?") },
            text = {
                Text(
                    "\"${target.productName}\" will be restored: the product comes back to inventory " +
                        "and the damaged record returns to the Active list.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revertMelt(target.id)
                    revertMeltTarget = null
                }) { Text("Revert Melt") }
            },
            dismissButton = {
                TextButton(onClick = { revertMeltTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DamagedRecordCard(
    row: DamagedRecordRow,
    canRevert: Boolean,
    canRevertMelt: Boolean,
    onRevert: () -> Unit,
    onMelt: () -> Unit,
    onRevertMelt: () -> Unit,
    onViewProduct: () -> Unit,
) {
    val dateSdf = remember { SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.US) }
    // Melted records are history — the underlying product is gone, so no nav and no actions.
    val cardModifier = if (row.isMelted) Modifier.fillMaxWidth()
    else Modifier.fillMaxWidth().clickable(onClick = onViewProduct)

    Card(
        modifier = cardModifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            row.productName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (row.isMelted) {
                            Text(
                                "MELTED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text("Reason: ${row.reason}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        dateSdf.format(Date(row.dateRecorded)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.notes?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canRevert && !row.isMelted) {
                    IconButton(onClick = onRevert) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Revert to Available",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (row.isMelted && canRevertMelt) {
                    IconButton(onClick = onRevertMelt) {
                        Icon(
                            Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Revert melt",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (canRevert && !row.isMelted) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Button(
                    onClick = onMelt,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        Icons.Default.Whatshot,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Melt")
                }
            }
        }
    }
}

@Composable
private fun RevertConfirmDialog(
    productName: String,
    onConfirm: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf("") }
    val isValid = reason.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Revert to Available") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Restore 1 unit of \"$productName\" back to inventory?",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required)") },
                    isError = reason.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (isValid) onConfirm(reason.trim()) },
                enabled = isValid,
            ) { Text("Revert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MeltConfirmDialog(
    productName: String,
    onConfirm: (notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Melt this item?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "\"$productName\" will be removed from inventory entirely. " +
                        "Use this when the piece has been melted down — it will not return to stock.",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
                onClick = { onConfirm(notes.trim().ifBlank { null }) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Melt") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
