package com.ykfj.inventory.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.usecase.auth.LoginResult
import com.ykfj.inventory.domain.usecase.auth.LoginUseCase
import com.ykfj.inventory.ui.components.SnackbarController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object Success : LoginUiState
    data class Error(val message: String) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Username and password are required")
            return
        }

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            when (val result = loginUseCase(username.trim(), password)) {
                is LoginResult.Success -> {
                    sessionManager.login(result.user)
                    _uiState.value = LoginUiState.Success
                    snackbarController.showSuccess("Welcome, ${result.user.name}")
                }
                is LoginResult.InvalidCredentials -> {
                    _uiState.value = LoginUiState.Error("Invalid username or password")
                }
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
