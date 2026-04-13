package com.ykfj.inventory.ui.navigation

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
import com.ykfj.inventory.domain.model.User
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

            // Bottom section: current user + logout
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))

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
