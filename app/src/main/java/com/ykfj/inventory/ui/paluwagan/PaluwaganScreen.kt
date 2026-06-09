package com.ykfj.inventory.ui.paluwagan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaluwaganScreen(
    onNavigateToDetail: (groupId: String) -> Unit,
    viewModel: PaluwaganViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val completedGroups = viewModel.completedGroups.collectAsLazyPagingItems()
    val snackbarHost = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var groupToDelete by remember { mutableStateOf<PaluwaganGroupRow?>(null) }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHost.showSnackbar(it); viewModel.clearError() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            if (state.canManage && !state.showCompleted) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create group")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Active / Completed tab toggle
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                SegmentedButton(
                    selected = !state.showCompleted,
                    onClick = { if (state.showCompleted) viewModel.toggleShowCompleted() },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Active") }
                SegmentedButton(
                    selected = state.showCompleted,
                    onClick = { if (!state.showCompleted) viewModel.toggleShowCompleted() },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text("Completed") }
            }

            if (!state.showCompleted) {
                // Active groups
                when {
                    state.isLoading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.groups.isEmpty() -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No active paluwagan groups", style = MaterialTheme.typography.bodyLarge)
                            if (state.canManage) {
                                Text(
                                    "Tap + to create one",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = state.groups, key = { it.id }) { row ->
                            PaluwaganGroupCard(
                                row = row,
                                onClick = { onNavigateToDetail(row.id) },
                            )
                        }
                    }
                }
            } else {
                // Completed groups (paged)
                CompletedGroupsList(
                    pagingItems = completedGroups,
                    isAdmin = state.isAdmin,
                    onNavigateToDetail = onNavigateToDetail,
                    onDelete = { groupToDelete = it },
                )
            }
        }
    }

    if (showCreateDialog) {
        CreatePaluwaganDialog(
            onConfirm = { name, amount, frequencyDays, slots, startDate, notes ->
                viewModel.createGroup(name, amount, frequencyDays, slots, startDate, notes)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    groupToDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text("Delete Group") },
            text = { Text("Permanently delete \"${group.name}\" and all its records? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hardDeleteGroup(group.id)
                    groupToDelete = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { groupToDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun CompletedGroupsList(
    pagingItems: LazyPagingItems<PaluwaganGroupRow>,
    isAdmin: Boolean,
    onNavigateToDetail: (String) -> Unit,
    onDelete: (PaluwaganGroupRow) -> Unit,
) {
    when {
        pagingItems.loadState.refresh is LoadState.Loading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        pagingItems.itemCount == 0 && pagingItems.loadState.refresh is LoadState.NotLoading -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text("No completed groups", style = MaterialTheme.typography.bodyLarge)
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                count = pagingItems.itemCount,
                key = pagingItems.itemKey { it.id },
            ) { index ->
                pagingItems[index]?.let { row ->
                    CompletedGroupCard(
                        row = row,
                        isAdmin = isAdmin,
                        onClick = { onNavigateToDetail(row.id) },
                        onDelete = { onDelete(row) },
                    )
                }
            }
            if (pagingItems.loadState.append is LoadState.Loading) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }
        }
    }
}

@Composable
private fun CompletedGroupCard(
    row: PaluwaganGroupRow,
    isAdmin: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    row.contributionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${row.totalSlots} slots · ${row.totalSlots} rounds completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val startLabel = remember(row.startDate) { dateFmt.format(Date(row.startDate)) }
                Text(
                    "Started $startLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isAdmin) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete group",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaluwaganGroupCard(
    row: PaluwaganGroupRow,
    onClick: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Name + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    row.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    row.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // Contribution + slots
            Text(
                row.contributionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${row.totalSlots} slots",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Round progress + current collector
            if (row.currentRound == 0) {
                Text(
                    "Not started",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val collectDeadlineMs = row.startDate +
                    (row.currentRound.toLong() * row.frequencyDays - 1) * 86_400_000L
                val collectDateLabel = remember(collectDeadlineMs) {
                    dateFmt.format(Date(collectDeadlineMs))
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Round ${row.currentRound} / ${row.totalSlots}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Collect: $collectDateLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                row.collectorName?.let { name ->
                    Text(
                        "Collecting: $name",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePaluwaganDialog(
    onConfirm: (name: String, contributionAmount: Double, frequencyDays: Int, totalSlots: Int, startDate: Long, notes: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var contributionRaw by rememberSaveable { mutableStateOf("") }
    var frequencyRaw by rememberSaveable { mutableStateOf("") }
    var totalSlotsRaw by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())

    val isValid = name.isNotBlank() &&
        contributionRaw.toDoubleOrNull()?.let { it > 0 } == true &&
        frequencyRaw.toIntOrNull()?.let { it >= 1 } == true &&
        totalSlotsRaw.toIntOrNull()?.let { it >= 2 } == true

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Paluwagan Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contributionRaw,
                    onValueChange = { contributionRaw = it },
                    label = { Text("Contribution Amount (₱)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = frequencyRaw,
                    onValueChange = { frequencyRaw = it },
                    label = { Text("Payment Interval (days, e.g. 7, 15, 30)") },
                    placeholder = { Text("e.g. 7") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = totalSlotsRaw,
                    onValueChange = { totalSlotsRaw = it },
                    label = { Text("Total Slots (min 2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { showDatePicker = true }) {
                    val dateLabel = datePickerState.selectedDateMillis?.let {
                        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(it))
                    } ?: "Select Start Date"
                    Text("Start Date: $dateLabel")
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
                    if (isValid) {
                        onConfirm(
                            name.trim(),
                            contributionRaw.toDouble(),
                            frequencyRaw.toInt(),
                            totalSlotsRaw.toInt(),
                            datePickerState.selectedDateMillis ?: System.currentTimeMillis(),
                            notes.trim().ifBlank { null },
                        )
                    }
                },
                enabled = isValid,
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
