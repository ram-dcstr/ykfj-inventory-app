package com.ykfj.inventory.ui.settings.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.user.ManageUsersUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserManagementUiState(
    val users: List<User> = emptyList(),
    val currentUserId: String? = null,
    val isAdmin: Boolean = false,
    val isWorking: Boolean = false,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UserManagementViewModel @Inject constructor(
    userRepository: UserRepository,
    private val manageUsers: ManageUsersUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(UserManagementUiState())

    val users: StateFlow<List<User>> = userRepository.observeActiveUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val state: StateFlow<UserManagementUiState> = _state.asStateFlow()

    init {
        val current = sessionManager.currentUser.value
        _state.update {
            it.copy(
                currentUserId = current?.id,
                isAdmin = current?.role == UserRole.ADMIN,
            )
        }
    }

    fun addUser(username: String, name: String, password: String, role: UserRole) {
        val actorId = _state.value.currentUserId ?: return
        _state.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val result = manageUsers.create(username, name, password, role, actorId)
            handleResult(result, success = "User '${username.trim().lowercase()}' added")
        }
    }

    fun editUser(userId: String, username: String, name: String, role: UserRole) {
        val actorId = _state.value.currentUserId ?: return
        _state.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val result = manageUsers.update(userId, username, name, role, actorId)
            handleResult(result, success = "User updated")
        }
    }

    fun resetPassword(userId: String, newPassword: String) {
        val actorId = _state.value.currentUserId ?: return
        _state.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val result = manageUsers.resetPassword(userId, newPassword, actorId)
            handleResult(result, success = "Password reset")
        }
    }

    fun deactivate(userId: String) {
        val actorId = _state.value.currentUserId ?: return
        _state.update { it.copy(isWorking = true, errorMessage = null, infoMessage = null) }
        viewModelScope.launch {
            val result = manageUsers.deactivate(userId, actorId)
            handleResult(result, success = "User deactivated")
        }
    }

    fun consumeMessages() {
        _state.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    private fun handleResult(result: ManageUsersUseCase.Result, success: String) {
        when (result) {
            is ManageUsersUseCase.Result.Success ->
                _state.update { it.copy(isWorking = false, infoMessage = success) }
            ManageUsersUseCase.Result.UsernameTaken ->
                _state.update { it.copy(isWorking = false, errorMessage = "That username is already in use") }
            ManageUsersUseCase.Result.InvalidUsername ->
                _state.update { it.copy(isWorking = false, errorMessage = "Username must be at least 3 characters") }
            ManageUsersUseCase.Result.InvalidName ->
                _state.update { it.copy(isWorking = false, errorMessage = "Name is required") }
            ManageUsersUseCase.Result.InvalidPassword ->
                _state.update { it.copy(isWorking = false, errorMessage = "Password must be at least 6 characters") }
            ManageUsersUseCase.Result.NotFound ->
                _state.update { it.copy(isWorking = false, errorMessage = "User not found") }
            ManageUsersUseCase.Result.CannotDeactivateSelf ->
                _state.update { it.copy(isWorking = false, errorMessage = "You cannot deactivate yourself") }
        }
    }
}
