package com.ykfj.inventory.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToUserManagement: () -> Unit = {},
    onNavigateToArchiveManager: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToActivityLog: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Admin tools first — these are what the user actually opens Settings for.
        NavigableSection(
            title = "User Management",
            subtitle = "Add, edit, deactivate users and reset passwords",
            icon = Icons.Default.PeopleAlt,
            onClick = onNavigateToUserManagement,
        )
        NavigableSection(
            title = "Archive Manager",
            subtitle = "Export archived records to CSV; admin can purge after export",
            icon = Icons.Default.Archive,
            onClick = onNavigateToArchiveManager,
        )
        NavigableSection(
            title = "Backup & Restore",
            subtitle = "Manual full backup to Downloads; daily auto DB backup; restore from ZIP",
            icon = Icons.Default.Backup,
            onClick = onNavigateToBackup,
        )
        NavigableSection(
            title = "Activity Log",
            subtitle = "Audit trail of every change; admin can export CSV",
            icon = Icons.Default.History,
            onClick = onNavigateToActivityLog,
        )
        SessionAppInfoSection(
            uiState = uiState,
            onTimeoutSelected = viewModel::setIdleTimeout,
            onSaveExportPassword = viewModel::setDailyExportPassword,
            onSaveChangeFloat = viewModel::setDefaultChangeFloat,
        )

        // Sync configuration is install-time noise — collapsed unless something needs attention.
        SyncSection(
            uiState = uiState,
            onRoleSelected = viewModel::setDeviceRole,
            onSave = viewModel::savePhoneConfig,
            onSyncNow = viewModel::syncNow,
        )
    }
}

// ── Shared section helpers ───────────────────────────────────────────────────

@Composable
private fun NavigableSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
}
