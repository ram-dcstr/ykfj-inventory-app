package com.ykfj.inventory.ui.settings.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.backup.BackupManager
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val lastManualAt: Long = 0L,
    val lastAutoAt: Long = 0L,
    val autoBackups: List<BackupManager.BackupSummary> = emptyList(),
    val isAdmin: Boolean = false,
    val isWorking: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    /** Set after a successful restore — UI should show "restart now" prompt. */
    val pendingRestart: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update {
            it.copy(
                lastManualAt = backupManager.lastManualBackupAt(),
                lastAutoAt = backupManager.lastAutoBackupAt(),
                autoBackups = backupManager.listAutoBackups(),
                isAdmin = sessionManager.currentUser.value?.role == UserRole.ADMIN,
            )
        }
    }

    fun consumeMessages() {
        _state.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    fun runManualBackup() {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, infoMessage = null, errorMessage = null) }
        viewModelScope.launch {
            when (val r = backupManager.createManualBackup()) {
                is BackupManager.CreateResult.Success -> {
                    val msg = "Backup saved to Downloads · ${r.displayName}"
                    _state.update {
                        it.copy(
                            isWorking = false,
                            infoMessage = msg,
                            lastManualAt = backupManager.lastManualBackupAt(),
                        )
                    }
                    snackbarController.showSuccess(msg)
                }
                is BackupManager.CreateResult.Failed ->
                    _state.update {
                        it.copy(isWorking = false, errorMessage = "Backup failed: ${r.message}")
                    }
            }
        }
    }

    fun restoreFromUri(uri: Uri) {
        if (_state.value.isWorking) return
        _state.update { it.copy(isWorking = true, infoMessage = null, errorMessage = null) }
        viewModelScope.launch {
            // Lightweight peek before nuking the DB
            if (!backupManager.peekArchive(uri)) {
                _state.update {
                    it.copy(
                        isWorking = false,
                        errorMessage = "Selected file does not look like a YKFJ backup",
                    )
                }
                return@launch
            }
            when (val r = backupManager.restoreFromZip(uri)) {
                BackupManager.RestoreResult.Success -> {
                    _state.update {
                        it.copy(
                            isWorking = false,
                            infoMessage = "Restore complete — restart required",
                            pendingRestart = true,
                        )
                    }
                    snackbarController.showSuccess("Restore complete · restart the app to load the new data")
                }
                is BackupManager.RestoreResult.InvalidArchive ->
                    _state.update { it.copy(isWorking = false, errorMessage = r.message) }
                is BackupManager.RestoreResult.Failed ->
                    _state.update {
                        it.copy(isWorking = false, errorMessage = "Restore failed: ${r.message}")
                    }
            }
        }
    }
}
