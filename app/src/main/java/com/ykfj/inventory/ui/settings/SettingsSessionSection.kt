package com.ykfj.inventory.ui.settings

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.data.remote.sync.SyncManager
import com.ykfj.inventory.domain.sync.DeviceRole
import com.ykfj.inventory.ui.auth.IdleTimeout

// ── Session & App Info section (Phase 6.6) ───────────────────────────────────

@Composable
internal fun SessionAppInfoSection(
    uiState: SettingsUiState,
    onTimeoutSelected: (IdleTimeout) -> Unit,
    onSaveExportPassword: (String) -> Unit,
    onSaveChangeFloat: (Double) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isAdmin = uiState.currentUserRole == UserRole.ADMIN
    val isAdminOrManager = isAdmin || uiState.currentUserRole == UserRole.MANAGER

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Session & App Info", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Idle ${uiState.idleTimeout.label} · v${uiState.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                IdleTimeoutChooser(
                    current = uiState.idleTimeout,
                    onSelected = onTimeoutSelected,
                )
                if (isAdmin) {
                    DailyExportPasswordField(
                        persisted = uiState.dailyExportPassword,
                        onSave = onSaveExportPassword,
                    )
                }
                if (isAdminOrManager) {
                    DefaultChangeFloatField(
                        persisted = uiState.defaultChangeFloat,
                        onSave = onSaveChangeFloat,
                    )
                }
                AppInfoBlock(
                    appVersion = uiState.appVersion,
                    deviceRole = uiState.deviceRole,
                    syncStatus = uiState.syncStatus,
                    isServerRunning = uiState.isServerRunning,
                    serverPort = uiState.serverPort,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdleTimeoutChooser(
    current: IdleTimeout,
    onSelected: (IdleTimeout) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Auto-logout when idle for…",
            style = MaterialTheme.typography.labelLarge,
        )
        val options = IdleTimeout.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = current == option,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) { Text(option.label) }
            }
        }
    }
}

@Composable
private fun DailyExportPasswordField(
    persisted: String,
    onSave: (String) -> Unit,
) {
    var field by remember(persisted) { mutableStateOf(persisted) }
    var visible by remember { mutableStateOf(false) }
    val dirty = field.isNotBlank() && field != persisted

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Daily sales export PDF password",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Required to open the daily-sales PDF exported from the Sold archive.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(
                            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (visible) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onSave(field) },
                enabled = dirty,
            ) { Text("Save") }
        }
    }
}

@Composable
private fun DefaultChangeFloatField(
    persisted: Double,
    onSave: (Double) -> Unit,
) {
    var field by remember(persisted) {
        mutableStateOf(if (persisted > 0) "%.2f".format(persisted) else "")
    }
    val parsed = field.toDoubleOrNull()
    val dirty = parsed != null && parsed >= 0 && parsed != persisted

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Default change float",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = "Opening cash auto-seeded into Daily Cash when the shop opens for a new day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount (₱)") },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                ),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { parsed?.let(onSave) },
                enabled = dirty,
            ) { Text("Save") }
        }
    }
}

@Composable
private fun AppInfoBlock(
    appVersion: String,
    deviceRole: DeviceRole,
    syncStatus: SyncManager.SyncStatus,
    isServerRunning: Boolean,
    serverPort: Int,
) {
    val roleLabel = when (deviceRole) {
        DeviceRole.TABLET -> "Tablet (Primary)"
        DeviceRole.PHONE -> "Phone (Secondary)"
    }
    val syncLine = when (deviceRole) {
        DeviceRole.TABLET ->
            if (isServerRunning) "Server running · port $serverPort" else "Server stopped"
        DeviceRole.PHONE -> when {
            syncStatus.isSyncing -> "Syncing now…"
            syncStatus.lastError != null -> "Last error: ${syncStatus.lastError}"
            syncStatus.lastSyncTime > 0L ->
                "Last synced ${DateUtils.getRelativeTimeSpanString(syncStatus.lastSyncTime)}"
            else -> "Never synced"
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        InfoRow("App version", appVersion)
        InfoRow("Device role", roleLabel)
        InfoRow("Sync", syncLine)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f),
        )
    }
}
