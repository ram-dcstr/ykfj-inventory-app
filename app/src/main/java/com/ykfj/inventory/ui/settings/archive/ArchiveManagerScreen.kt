package com.ykfj.inventory.ui.settings.archive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.domain.usecase.archive.ArchivableRecordType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveManagerScreen(
    onNavigateUp: () -> Unit,
    viewModel: ArchiveManagerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showPurgeConfirm by remember { mutableStateOf(false) }

    // Success now routes through the global SnackbarController. Local host
    // still handles errors inline.
    LaunchedEffect(state.errorMessage, state.infoMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessages()
        }
        if (state.infoMessage != null) viewModel.consumeMessages()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Archive Manager") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Export archived records to a CSV in Downloads, then optionally hard-delete them from the database.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Type selection
            Text("Record type", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ArchivableRecordType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.setType(type) },
                        label = { Text(type.label) },
                    )
                }
            }

            HorizontalDivider()

            // Date range
            Text("Date range", style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DateField(
                    label = "From",
                    millis = state.startMillis,
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    label = "To",
                    millis = state.endMillis,
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!state.rangeIsValid) {
                Text(
                    text = "End date must be on or after the start date.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            HorizontalDivider()

            // Preview
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "${state.previewCount} archived ${state.type.label.lowercase()} record(s) match",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Filename: ykfj-archive-${state.type.csvSlug}-…csv",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Actions
            Button(
                onClick = { viewModel.export(thenPurge = false) },
                enabled = !state.isWorking && state.previewCount > 0 && state.rangeIsValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isWorking) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.padding(horizontal = 6.dp))
                }
                Icon(Icons.Default.FileDownload, contentDescription = null)
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text("Export to CSV")
            }
            if (state.isAdmin) {
                OutlinedButton(
                    onClick = { showPurgeConfirm = true },
                    enabled = !state.isWorking && state.previewCount > 0 && state.rangeIsValid,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text("Export & Delete")
                }
            }
        }
    }

    if (showStartPicker) {
        SimpleDatePickerDialog(
            initialMillis = state.startMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                viewModel.setStartDate(it)
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        SimpleDatePickerDialog(
            initialMillis = state.endMillis,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                viewModel.setEndDate(it)
                showEndPicker = false
            },
        )
    }
    if (showPurgeConfirm) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirm = false },
            title = { Text("Export and permanently delete?") },
            text = {
                Text(
                    "This will export ${state.previewCount} record(s) to CSV in Downloads, " +
                        "then HARD DELETE them from the database. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPurgeConfirm = false
                        viewModel.export(thenPurge = true)
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Export & Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DateField(
    label: String,
    millis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.Start) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(fmt.format(Date(millis)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let(onConfirm)
                },
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
