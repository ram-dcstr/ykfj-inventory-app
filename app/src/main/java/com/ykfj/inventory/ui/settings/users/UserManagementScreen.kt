package com.ykfj.inventory.ui.settings.users

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    onNavigateUp: () -> Unit,
    viewModel: UserManagementViewModel = hiltViewModel(),
) {
    val users by viewModel.users.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var addDialogOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<User?>(null) }
    var deactivateTarget by remember { mutableStateOf<User?>(null) }
    var resetPasswordTarget by remember { mutableStateOf<User?>(null) }

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
                title = { Text("User Management") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.isAdmin) {
                ExtendedFloatingActionButton(
                    onClick = { addDialogOpen = true },
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text = { Text("Add User") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!state.isAdmin) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Only administrators can manage users",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        if (users.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No active users", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(users, key = { it.id }) { user ->
                UserCard(
                    user = user,
                    isCurrent = user.id == state.currentUserId,
                    onEdit = { editing = user },
                    onResetPassword = { resetPasswordTarget = user },
                    onDeactivate = { deactivateTarget = user },
                )
            }
        }
    }

    if (addDialogOpen) {
        UserFormDialog(
            editing = null,
            existingUsername = null,
            onDismiss = { addDialogOpen = false },
            onSubmit = { input ->
                viewModel.addUser(input.username, input.name, input.password.orEmpty(), input.role)
                addDialogOpen = false
            },
        )
    }

    editing?.let { target ->
        UserFormDialog(
            editing = target,
            existingUsername = target.username,
            onDismiss = { editing = null },
            onSubmit = { input ->
                viewModel.editUser(target.id, input.username, input.name, input.role)
                editing = null
            },
        )
    }

    resetPasswordTarget?.let { target ->
        ResetPasswordDialog(
            user = target,
            onDismiss = { resetPasswordTarget = null },
            onSubmit = { newPassword ->
                viewModel.resetPassword(target.id, newPassword)
                resetPasswordTarget = null
            },
        )
    }

    deactivateTarget?.let { target ->
        DeactivateConfirmDialog(
            user = target,
            onDismiss = { deactivateTarget = null },
            onConfirm = {
                viewModel.deactivate(target.id)
                deactivateTarget = null
            },
        )
    }
}

@Composable
private fun UserCard(
    user: User,
    isCurrent: Boolean,
    onEdit: () -> Unit,
    onResetPassword: () -> Unit,
    onDeactivate: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "(You)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Text(
                    text = "@${user.username}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                RoleBadge(user.role)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onResetPassword) {
                Icon(Icons.Default.Password, contentDescription = "Reset password")
            }
            IconButton(
                onClick = onDeactivate,
                enabled = !isCurrent,
            ) {
                Icon(
                    imageVector = Icons.Default.PersonOff,
                    contentDescription = "Deactivate",
                    tint = if (isCurrent) MaterialTheme.colorScheme.outline
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun RoleBadge(role: UserRole) {
    val (label, color) = when (role) {
        UserRole.ADMIN -> "Admin" to MaterialTheme.colorScheme.primary
        UserRole.MANAGER -> "Manager" to MaterialTheme.colorScheme.tertiary
        UserRole.STAFF -> "Staff" to MaterialTheme.colorScheme.secondary
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = color.copy(alpha = 0.15f),
            disabledLabelColor = color,
        ),
    )
}
