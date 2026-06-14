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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.data.remote.sync.ResolvedService
import com.ykfj.inventory.data.remote.sync.SyncManager
import com.ykfj.inventory.domain.sync.DeviceRole

// ── Sync section (collapsed-by-default) ──────────────────────────────────────

@Composable
internal fun SyncSection(
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

internal val GoodGreen = Color(0xFF1B5E20)
