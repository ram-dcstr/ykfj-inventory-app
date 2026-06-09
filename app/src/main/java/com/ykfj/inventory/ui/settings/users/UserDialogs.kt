package com.ykfj.inventory.ui.settings.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User

data class UserFormInput(
    val username: String,
    val name: String,
    val role: UserRole,
    /** Plaintext password — only present in the Add flow. Edit flow uses [ResetPasswordDialog]. */
    val password: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserFormDialog(
    editing: User?,
    /** Existing username when editing — disables the uniqueness warning when unchanged. */
    @Suppress("UNUSED_PARAMETER") existingUsername: String?,
    onDismiss: () -> Unit,
    onSubmit: (UserFormInput) -> Unit,
) {
    val isEdit = editing != null
    var username by rememberSaveable(editing?.id) { mutableStateOf(editing?.username.orEmpty()) }
    var name by rememberSaveable(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var role by rememberSaveable(editing?.id) { mutableStateOf(editing?.role ?: UserRole.STAFF) }
    var password by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }

    val passwordOk = isEdit || password.length >= 6
    val canSubmit = username.trim().length >= 3 && name.trim().isNotBlank() && passwordOk

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit User" else "Add User") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    supportingText = { Text("Letters/numbers, at least 3 characters") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Role", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val roles = listOf(UserRole.ADMIN, UserRole.MANAGER, UserRole.STAFF)
                    roles.forEachIndexed { index, r ->
                        SegmentedButton(
                            selected = role == r,
                            onClick = { role = r },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = roles.size),
                        ) {
                            Text(
                                when (r) {
                                    UserRole.ADMIN -> "Admin"
                                    UserRole.MANAGER -> "Manager"
                                    UserRole.STAFF -> "Staff"
                                },
                            )
                        }
                    }
                }

                if (!isEdit) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Initial Password") },
                        supportingText = { Text("At least 6 characters") },
                        singleLine = true,
                        visualTransformation = if (passVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { passVisible = !passVisible }) {
                                Icon(
                                    imageVector = if (passVisible) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (passVisible) "Hide password"
                                    else "Show password",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "Use the key icon on the user row to reset password.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = {
                    onSubmit(
                        UserFormInput(
                            username = username,
                            name = name,
                            role = role,
                            password = if (isEdit) null else password,
                        ),
                    )
                },
            ) { Text(if (isEdit) "Save" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ResetPasswordDialog(
    user: User,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    val canSubmit = password.length >= 6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Set a new password for ${user.name} (@${user.username}).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("New Password") },
                    supportingText = { Text("At least 6 characters") },
                    singleLine = true,
                    visualTransformation = if (passVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passVisible = !passVisible }) {
                            Icon(
                                imageVector = if (passVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Note: password resets only apply on this device. To change password " +
                        "across devices, do it on the tablet directly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = { onSubmit(password) },
            ) { Text("Reset") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DeactivateConfirmDialog(
    user: User,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Deactivate user?") },
        text = {
            Text(
                "${user.name} (@${user.username}) will no longer be able to log in. " +
                    "Their existing records and audit history are preserved.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Deactivate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
