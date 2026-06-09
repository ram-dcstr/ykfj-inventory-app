package com.ykfj.inventory.ui.settings.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HighlightOff
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.ActivityLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(
    onNavigateUp: () -> Unit,
    viewModel: ActivityLogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showUserMenu by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage, state.infoMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessages()
        }
        state.infoMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity Log") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.canExport) {
                        IconButton(onClick = viewModel::export, enabled = !state.isWorking) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export CSV")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            FilterBar(
                filter = filter,
                userNames = state.userNamesById,
                userFilterLocked = state.userFilterLocked,
                canSelectUser = state.canSelectUser,
                onUserClick = { showUserMenu = true },
                onActionClick = { showActionMenu = true },
                onStartClick = { showStartPicker = true },
                onEndClick = { showEndPicker = true },
            )
            HorizontalDivider()

            if (state.logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No activity in this range",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.logs, key = { it.id }) { log ->
                        LogRow(log = log, userName = state.userNamesById[log.userId] ?: log.userId)
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        SimpleDatePickerDialog(
            initialMillis = filter.startMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = {
                viewModel.setStartDate(it)
                showStartPicker = false
            },
        )
    }
    if (showEndPicker) {
        SimpleDatePickerDialog(
            initialMillis = filter.endMillis,
            onDismiss = { showEndPicker = false },
            onConfirm = {
                viewModel.setEndDate(it)
                showEndPicker = false
            },
        )
    }

    if (showUserMenu) {
        UserPickerDialog(
            users = state.users,
            selectedId = filter.userId,
            onDismiss = { showUserMenu = false },
            onSelect = {
                viewModel.setUserFilter(it)
                showUserMenu = false
            },
        )
    }

    if (showActionMenu) {
        ActionPickerDialog(
            selected = filter.action,
            onDismiss = { showActionMenu = false },
            onSelect = {
                viewModel.setActionFilter(it)
                showActionMenu = false
            },
        )
    }
}

@Composable
private fun FilterBar(
    filter: ActivityLogFilter,
    userNames: Map<String, String>,
    userFilterLocked: Boolean,
    canSelectUser: Boolean,
    onUserClick: () -> Unit,
    onActionClick: () -> Unit,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
) {
    val dateFmt = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val userLabel = when {
            userFilterLocked -> "Me only"
            filter.userId == null -> "All users"
            else -> userNames[filter.userId] ?: "User"
        }
        FilterChip(
            selected = filter.userId != null,
            enabled = canSelectUser,
            onClick = onUserClick,
            label = { Text(userLabel) },
        )
        FilterChip(
            selected = filter.action != null,
            onClick = onActionClick,
            label = { Text(filter.action?.label() ?: "All actions") },
        )
        FilterChip(
            selected = true,
            onClick = onStartClick,
            label = { Text("From: ${dateFmt.format(Date(filter.startMillis))}") },
        )
        FilterChip(
            selected = true,
            onClick = onEndClick,
            label = { Text("To: ${dateFmt.format(Date(filter.endMillis))}") },
        )
    }
}

@Composable
private fun LogRow(log: ActivityLog, userName: String) {
    var expanded by remember(log.id) { mutableStateOf(false) }
    val canExpand = !log.oldValue.isNullOrBlank() || !log.newValue.isNullOrBlank()
    val timeFmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canExpand) { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = log.action.icon(),
                contentDescription = null,
                tint = log.action.tint(),
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = log.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = timeFmt.format(Date(log.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = "·", color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(log.action.label()) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = log.action.tint().copy(alpha = 0.12f),
                            disabledLabelColor = log.action.tint(),
                        ),
                    )
                }
                AnimatedVisibility(visible = expanded) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        log.entityType?.let {
                            DiffLine("entity", "${it}${log.entityId?.let { id -> " ($id)" }.orEmpty()}")
                        }
                        log.oldValue?.takeIf { it.isNotBlank() }?.let { DiffLine("before", it) }
                        log.newValue?.takeIf { it.isNotBlank() }?.let { DiffLine("after", it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffLine(label: String, value: String) {
    Row {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun UserPickerDialog(
    users: List<com.ykfj.inventory.domain.model.User>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by user") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("All users", fontWeight = if (selectedId == null) FontWeight.Bold else null)
                }
                HorizontalDivider()
                users.forEach { u ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(u.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(u.name, fontWeight = if (selectedId == u.id) FontWeight.Bold else null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "@${u.username}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun ActionPickerDialog(
    selected: ActivityAction?,
    onDismiss: () -> Unit,
    onSelect: (ActivityAction?) -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by action") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(null) }.padding(vertical = 12.dp),
                ) {
                    Text("All actions", fontWeight = if (selected == null) FontWeight.Bold else null)
                }
                HorizontalDivider()
                ActivityAction.entries.forEach { a ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(a) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(a.icon(), contentDescription = null, tint = a.tint(), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(a.label(), fontWeight = if (selected == a) FontWeight.Bold else null)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
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
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onConfirm) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun ActivityAction.icon(): ImageVector = when (this) {
    ActivityAction.LOGIN -> Icons.AutoMirrored.Filled.Login
    ActivityAction.LOGOUT -> Icons.AutoMirrored.Filled.Logout
    ActivityAction.CREATE -> Icons.Default.AddCircle
    ActivityAction.UPDATE -> Icons.Default.Edit
    ActivityAction.DELETE -> Icons.Default.Delete
    ActivityAction.SELL -> Icons.Default.ShoppingCart
    ActivityAction.LAYAWAY -> Icons.Default.AttachMoney
    ActivityAction.DAMAGE -> Icons.Default.ReportProblem
    ActivityAction.REVERT -> Icons.Default.Undo
    ActivityAction.PAYMENT -> Icons.Default.Payments
    else -> Icons.Default.History
}

@Composable
private fun ActivityAction.tint() = when (this) {
    ActivityAction.DELETE, ActivityAction.DAMAGE -> MaterialTheme.colorScheme.error
    ActivityAction.SELL, ActivityAction.PAYMENT -> MaterialTheme.colorScheme.tertiary
    ActivityAction.LOGIN, ActivityAction.LOGOUT -> MaterialTheme.colorScheme.outline
    else -> MaterialTheme.colorScheme.primary
}

private fun ActivityAction.label(): String =
    name.lowercase().replaceFirstChar { it.uppercase() }
