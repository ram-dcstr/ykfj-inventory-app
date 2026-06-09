package com.ykfj.inventory.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.data.remote.sync.SyncManager
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.sync.DeviceRole
import com.ykfj.inventory.ui.theme.AlertBadgeRed

@Composable
fun SidebarContent(
    currentUser: User?,
    selectedScreen: Screen,
    layawayOverdueCount: Int,
    paluwaganDueCount: Int,
    onScreenSelected: (Screen) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    syncStatus: SyncManager.SyncStatus = SyncManager.SyncStatus(),
    deviceRole: DeviceRole = DeviceRole.TABLET,
    isServerRunning: Boolean = false,
    onSyncTap: () -> Unit = {},
) {
    PermanentDrawerSheet(
        modifier = modifier.width(280.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp),
        ) {
            // App title
            Text(
                text = "YKFJ Inventory",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))

            // Navigation items
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                val visibleScreens = if (currentUser != null) {
                    Screen.sidebarItemsFor(currentUser.role)
                } else {
                    Screen.allScreens
                }
                visibleScreens.forEach { screen ->
                    val badgeCount = when (screen) {
                        Screen.Layaway -> layawayOverdueCount
                        Screen.Paluwagan -> paluwaganDueCount
                        else -> 0
                    }

                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = null,
                            )
                        },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(text = screen.label)
                                if (badgeCount > 0) {
                                    Badge(
                                        containerColor = AlertBadgeRed,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ) {
                                        Text(text = badgeCount.toString())
                                    }
                                }
                            }
                        },
                        selected = selectedScreen == screen,
                        onClick = { onScreenSelected(screen) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }

            // Bottom section: sync status + current user + logout
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(4.dp))

            SyncStatusBar(
                status = syncStatus,
                deviceRole = deviceRole,
                isServerRunning = isServerRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !syncStatus.isSyncing) { onSyncTap() }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )

            if (currentUser != null) {
                UserFooter(
                    user = currentUser,
                    onLogout = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun UserFooter(
    user: User,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            RoleBadge(role = user.role)
        }

        TextButton(onClick = onLogout) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = "Logout",
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SyncStatusBar(
    status: SyncManager.SyncStatus,
    deviceRole: DeviceRole,
    isServerRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    val isError: Boolean
    val label: String
    when (deviceRole) {
        DeviceRole.TABLET -> {
            isError = !isServerRunning
            label = if (isServerRunning) "Server running · tap for info" else "Server stopped"
        }
        DeviceRole.PHONE -> {
            isError = status.lastError != null
            val base = when {
                status.isSyncing -> "Syncing…"
                status.lastError != null -> "Sync failed · tap to retry"
                status.lastSyncTime > 0L -> {
                    val minutesAgo = ((System.currentTimeMillis() - status.lastSyncTime) / 60_000).toInt()
                    when {
                        minutesAgo < 1 -> "Synced just now · tap to sync"
                        minutesAgo == 1 -> "Synced 1 min ago · tap to sync"
                        else -> "Synced $minutesAgo min ago · tap to sync"
                    }
                }
                else -> "Not synced · tap to sync"
            }
            label = if (status.pendingCount > 0) "$base · ${status.pendingCount} pending" else base
        }
    }
    val icon = if (isError) Icons.Default.SyncProblem else Icons.Default.Sync
    val tint = if (isError) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RoleBadge(role: com.ykfj.inventory.data.local.db.enums.UserRole) {
    val label = when (role) {
        com.ykfj.inventory.data.local.db.enums.UserRole.ADMIN -> "Admin"
        com.ykfj.inventory.data.local.db.enums.UserRole.MANAGER -> "Manager"
        com.ykfj.inventory.data.local.db.enums.UserRole.STAFF -> "Staff"
    }

    Badge(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
