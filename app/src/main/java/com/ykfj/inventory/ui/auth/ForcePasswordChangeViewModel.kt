package com.ykfj.inventory.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.usecase.auth.ChangeOwnPasswordUseCase
import com.ykfj.inventory.ui.components.SnackbarController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ForcePasswordChangeUiState {
    data object Idle : ForcePasswordChangeUiState
    data object Saving : ForcePasswordChangeUiState
    data class Error(val message: String) : ForcePasswordChangeUiState
}

/**
 * Drives the one-time "set your password" screen shown when the signed-in user
 * has `mustChangePassword`. On success it updates the live session so the gate in
 * MainActivity falls through to the app — no re-login needed.
 */
@HiltViewModel
class ForcePasswordChangeViewModel @Inject constructor(
    private val changeOwnPassword: ChangeOwnPasswordUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForcePasswordChangeUiState>(ForcePasswordChangeUiState.Idle)
    val uiState: StateFlow<ForcePasswordChangeUiState> = _uiState.asStateFlow()

    val minLength = ChangeOwnPasswordUseCase.MIN_PASSWORD_LENGTH

    fun submit(newPassword: String, confirmPassword: String) {
        val userId = sessionManager.currentUser.value?.id ?: run {
            _uiState.value = ForcePasswordChangeUiState.Error("Session expired — please log in again")
            return
        }
        if (newPassword.length < minLength) {
            _uiState.value = ForcePasswordChangeUiState.Error("Password must be at least $minLength characters")
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.value = ForcePasswordChangeUiState.Error("Passwords don't match")
            return
        }

        _uiState.value = ForcePasswordChangeUiState.Saving
        viewModelScope.launch {
            when (val result = changeOwnPassword(userId, newPassword)) {
                is ChangeOwnPasswordUseCase.Result.Success -> {
                    sessionManager.updateCurrentUser(result.user)
                    snackbarController.showSuccess("Password updated")
                }
                ChangeOwnPasswordUseCase.Result.TooShort ->
                    _uiState.value = ForcePasswordChangeUiState.Error("Password must be at least $minLength characters")
                ChangeOwnPasswordUseCase.Result.SameAsCurrent ->
                    _uiState.value = ForcePasswordChangeUiState.Error("Choose a password different from the current one")
                ChangeOwnPasswordUseCase.Result.NotFound ->
                    _uiState.value = ForcePasswordChangeUiState.Error("Session expired — please log in again")
            }
        }
    }

    fun logout() = sessionManager.logout()
}
