package com.ykfj.inventory.ui.metalrates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.util.CurrencyFormatter

@Composable
fun MetalRatesScreen(
    viewModel: MetalRatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<MetalRate?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openAddForm,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Rate") },
            )
        },
    ) { padding ->
        Body(
            state = state,
            contentPadding = padding,
            onEdit = viewModel::openEditForm,
            onDelete = { pendingDelete = it },
        )
    }

    if (state.isFormOpen) {
        MetalRateFormDialog(
            editing = state.editing,
            onDismiss = viewModel::closeForm,
            onSubmit = viewModel::submit,
        )
    }

    pendingDelete?.let { rate ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete metal rate?") },
            text = {
                Text(
                    "Delete '${rate.name}' (${CurrencyFormatter.format(rate.pricePerGram)}/g)? " +
                        "This cannot be undone if no products reference it.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(rate)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Body(
    state: MetalRatesUiState,
    contentPadding: PaddingValues,
    onEdit: (MetalRate) -> Unit,
    onDelete: (MetalRate) -> Unit,
) {
    when {
        state.isLoading -> Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        state.rates.isEmpty() -> Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No metal rates yet.\nTap \"Add Rate\" to create your first one.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        else -> LazyColumn(
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 12.dp,
                bottom = contentPadding.calculateBottomPadding() + 96.dp,
                start = 16.dp,
                end = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items = state.rates, key = { it.id }) { rate ->
                MetalRateRow(
                    rate = rate,
                    onEdit = { onEdit(rate) },
                    onDelete = { onDelete(rate) },
                )
            }
        }
    }
}

@Composable
private fun MetalRateRow(
    rate: MetalRate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rate.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${CurrencyFormatter.format(rate.pricePerGram)} / gram",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit ${rate.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete ${rate.name}",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
