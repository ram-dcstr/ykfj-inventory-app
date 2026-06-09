package com.ykfj.inventory.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PeopleAlt
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Tablet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.data.remote.sync.ResolvedService
import com.ykfj.inventory.data.remote.sync.SyncManager
import com.ykfj.inventory.domain.sync.DeviceRole
import com.ykfj.inventory.ui.auth.IdleTimeout

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

// ── Sync section (collapsed-by-default) ──────────────────────────────────────

@Composable
private fun SyncSection(
    uiState: SettingsUiState,
    onRoleSelected: (DeviceRole) -> Unit,
    onSave: (ip: String, username: String, password: String) -> Unit,
    onSyncNow: () -> Unit,
) {
    // `remember(isSyncHealthy)` resets every time the underlying health flips, so
    // the section auto-expands the moment something breaks and auto-collapses
    // again once it's resolved. Within a single health state the user's manual
    // toggle (chevron tap) is preserved.
    var expanded by remember(uiState.isSyncHealthy) { mutableStateOf(!uiState.isSyncHealthy) }

    Column {
        SyncSummaryRow(
            uiState = uiState,
            expanded = expanded,
            onToggle = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RoleChooser(
                    currentRole = uiState.deviceRole,
                    isSaving = uiState.isSaving,
                    onRoleSelected = onRoleSelected,
                )
                if (uiState.deviceRole == DeviceRole.TABLET) {
                    TabletSyncDetails(
                        port = uiState.serverPort,
                        ownLanIp = uiState.ownLanIp,
                        ownTailscaleIp = uiState.ownTailscaleIp,
                    )
                } else {
                    PhoneSyncDetails(
                        tabletIp = uiState.tabletIp,
                        syncUsername = uiState.syncUsername,
                        syncStatus = uiState.syncStatus,
                        nsdDiscovered = uiState.nsdDiscovered,
                        isSaving = uiState.isSaving,
                        onSave = onSave,
                        onSyncNow = onSyncNow,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncSummaryRow(
    uiState: SettingsUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val healthy = uiState.isSyncHealthy
    val dotColor = if (healthy) GoodGreen else MaterialTheme.colorScheme.error
    val (title, detail) = summaryFor(uiState)

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .let {
                        // Tiny status dot in place of an icon — quieter than a full glyph.
                        it
                    },
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = dotColor)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!healthy) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider()
    }
}

private fun summaryFor(state: SettingsUiState): Pair<String, String> = when (state.deviceRole) {
    DeviceRole.TABLET -> "Sync server" to if (state.isServerRunning) {
        "Running on port ${state.serverPort}"
    } else {
        "Stopped"
    }
    DeviceRole.PHONE -> "Sync" to when {
        state.tabletIp.isBlank() -> "Not configured — set the tablet's address"
        state.syncStatus.lastError != null -> state.syncStatus.lastError!!
        state.syncStatus.isSyncing -> "Syncing…"
        state.syncStatus.lastSyncTime > 0L ->
            "Last synced ${DateUtils.getRelativeTimeSpanString(state.syncStatus.lastSyncTime)}"
        else -> "Waiting for first sync…"
    }
}

// ── Expanded — role chooser ──────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleChooser(
    currentRole: DeviceRole,
    isSaving: Boolean,
    onRoleSelected: (DeviceRole) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "This device is the…",
            style = MaterialTheme.typography.labelLarge,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = currentRole == DeviceRole.TABLET,
                onClick = { if (!isSaving) onRoleSelected(DeviceRole.TABLET) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = {
                    SegmentedButtonDefaults.Icon(active = currentRole == DeviceRole.TABLET) {
                        Icon(
                            imageVector = Icons.Default.Tablet,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
            ) { Text("Tablet (Primary)") }
            SegmentedButton(
                selected = currentRole == DeviceRole.PHONE,
                onClick = { if (!isSaving) onRoleSelected(DeviceRole.PHONE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = {
                    SegmentedButtonDefaults.Icon(active = currentRole == DeviceRole.PHONE) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                        )
                    }
                },
            ) { Text("Phone (Secondary)") }
        }
    }
}

// ── Expanded — Tablet ────────────────────────────────────────────────────────

@Composable
private fun TabletSyncDetails(port: Int, ownLanIp: String?, ownTailscaleIp: String?) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Phones on the same WiFi should auto-discover this tablet on port $port. " +
                "If auto-discovery isn't working (some routers block it), enter the WiFi address " +
                "below on the phone manually. Use the Tailscale address only for remote access.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (ownLanIp != null) {
            CopyableField(
                label = "This tablet's WiFi address",
                value = "$ownLanIp:$port",
                onCopy = { copyToClipboard(context, "WiFi IP", ownLanIp) },
            )
        } else {
            Text(
                text = "WiFi address not detected. Connect this tablet to WiFi so phones can find it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (ownTailscaleIp != null) {
            CopyableField(
                label = "This tablet's Tailscale address",
                value = ownTailscaleIp,
                onCopy = { copyToClipboard(context, "Tailscale IP", ownTailscaleIp) },
            )
        } else {
            Text(
                text = "Tailscale not detected. Install and sign in to Tailscale on this tablet " +
                    "if you want phones to sync from outside the shop.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CopyableField(label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        IconButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy $label")
        }
    }
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
}

// ── NSD live-diagnostic banner (phone-side) ─────────────────────────────────

/**
 * Live readout of what NSD (mDNS) auto-discovery has found on the local WiFi.
 * Helps the user see whether same-WiFi sync is working without combing through logcat.
 *
 *  - GREEN  "Tablet found on WiFi at 192.168.x.x" — discovery succeeded, sync will
 *           use this LAN address even if a Tailscale fallback is configured.
 *  - AMBER  "Searching for tablet on WiFi… (using {fallbackIp})" — no NSD response
 *           yet. Either the tablet's server isn't running, the router is blocking
 *           mDNS, or both devices are on different SSIDs/VLANs.
 *  - GREY   "Searching for tablet on WiFi…" — no NSD response and no fallback set;
 *           sync won't work until one of those lands.
 */
@Composable
private fun NsdDiscoveryStatus(discovered: ResolvedService?, fallbackIp: String) {
    val (label, tint) = when {
        discovered != null ->
            "Tablet found on WiFi at ${discovered.host}:${discovered.port} — sync will use this." to
                MaterialTheme.colorScheme.primary
        fallbackIp.isNotBlank() ->
            "Searching for tablet on WiFi… falling back to $fallbackIp" to
                MaterialTheme.colorScheme.tertiary
        else ->
            "Searching for tablet on WiFi… (no address entered as fallback)" to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = tint,
    )
}

// ── Expanded — Phone ─────────────────────────────────────────────────────────

@Composable
private fun PhoneSyncDetails(
    tabletIp: String,
    syncUsername: String,
    syncStatus: SyncManager.SyncStatus,
    nsdDiscovered: ResolvedService?,
    isSaving: Boolean,
    onSave: (ip: String, username: String, password: String) -> Unit,
    onSyncNow: () -> Unit,
) {
    var ipField by remember(tabletIp) { mutableStateOf(tabletIp) }
    var userField by remember(syncUsername) { mutableStateOf(syncUsername) }
    var passField by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "On the same WiFi the phone auto-discovers the tablet (no address needed). " +
                "Enter an address only if auto-discovery isn't working on your router, or for " +
                "remote access from outside the shop via Tailscale.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NsdDiscoveryStatus(discovered = nsdDiscovered, fallbackIp = tabletIp)

        OutlinedTextField(
            value = ipField,
            onValueChange = { ipField = it },
            label = { Text("Tablet Address (LAN or Tailscale)") },
            placeholder = { Text("192.168.1.50  or  100.64.x.y") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = userField,
            onValueChange = { userField = it },
            label = { Text("Sync Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = passField,
            onValueChange = { passField = it },
            label = { Text("Sync Password") },
            singleLine = true,
            visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        imageVector = if (passVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passVisible) "Hide password" else "Show password",
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSave(ipField, userField, passField) },
                enabled = !isSaving,
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            Button(
                onClick = onSyncNow,
                enabled = !syncStatus.isSyncing && !isSaving,
                modifier = Modifier.weight(1f),
            ) { Text(if (syncStatus.isSyncing) "Syncing…" else "Sync Now") }
        }
        syncStatus.lastError?.let { err ->
            Text(
                text = "Error: $err",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (syncStatus.lastSyncTime > 0L) {
            Text(
                text = "Last synced: ${DateUtils.getRelativeTimeSpanString(syncStatus.lastSyncTime)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

// ── Session & App Info section (Phase 6.6) ───────────────────────────────────

@Composable
private fun SessionAppInfoSection(
    uiState: SettingsUiState,
    onTimeoutSelected: (IdleTimeout) -> Unit,
    onSaveExportPassword: (String) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isAdmin = uiState.currentUserRole == UserRole.ADMIN

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

private val GoodGreen = Color(0xFF1B5E20)
