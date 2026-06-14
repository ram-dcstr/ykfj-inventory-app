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
import androidx.compose.ui.unit.sp
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
            // App brand — stacked wordmark echoing the logo: the name in a refined,
            // letter-spaced uppercase over a smaller, wider-spaced "FINE JEWELRY"
            // tagline in the system gold. Two-tone (dark name + gold tagline).
            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = "YRISH KIM",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "FINE JEWELRY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 5.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

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
                val mainItems = visibleScreens.filter { it.group == SidebarGroup.MAIN }
                val manageItems = visibleScreens.filter { it.group == SidebarGroup.MANAGE }

                mainItems.forEach { screen ->
                    SidebarNavItem(
                        screen = screen,
                        selected = selectedScreen == screen,
                        layawayOverdueCount = layawayOverdueCount,
                        paluwaganDueCount = paluwaganDueCount,
                        onClick = { onScreenSelected(screen) },
                    )
                }

                // "Manage" zone — only rendered when the role has items here (Staff don't).
                if (manageItems.isNotEmpty()) {
                    Text(
                        text = "MANAGE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                    manageItems.forEach { screen ->
                        SidebarNavItem(
                            screen = screen,
                            selected = selectedScreen == screen,
                            layawayOverdueCount = layawayOverdueCount,
                            paluwaganDueCount = paluwaganDueCount,
                            onClick = { onScreenSelected(screen) },
                        )
                    }
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
private fun SidebarNavItem(
    screen: Screen,
    selected: Boolean,
    layawayOverdueCount: Int,
    paluwaganDueCount: Int,
    onClick: () -> Unit,
) {
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
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
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
