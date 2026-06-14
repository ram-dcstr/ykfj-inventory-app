package com.ykfj.inventory.ui.settings.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.ActivityLog
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.ExportActivityLogUseCase
import com.ykfj.inventory.domain.usecase.activitylog.GetActivityLogsUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class ActivityLogFilter(
    val userId: String? = null,
    val action: ActivityAction? = null,
    /** Inclusive start of day. */
    val startMillis: Long = startOfNDaysAgo(7),
    /** Inclusive end of day. */
    val endMillis: Long = endOfDay(System.currentTimeMillis()),
) {
    val rangeIsValid: Boolean get() = startMillis <= endMillis
}

data class ActivityLogUiState(
    val logs: List<ActivityLog> = emptyList(),
    val users: List<User> = emptyList(),
    val userNamesById: Map<String, String> = emptyMap(),
    /** Whether the role-aware filter is locking the user picker (Staff). */
    val userFilterLocked: Boolean = false,
    val canSelectUser: Boolean = false,
    val canExport: Boolean = false,
    val isWorking: Boolean = false,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ActivityLogViewModel @Inject constructor(
    private val getLogs: GetActivityLogsUseCase,
    private val exportLogs: ExportActivityLogUseCase,
    private val sessionManager: SessionManager,
    userRepository: UserRepository,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val _filter = MutableStateFlow(ActivityLogFilter())
    val filter: StateFlow<ActivityLogFilter> = _filter.asStateFlow()

    private val _ui = MutableStateFlow(ActivityLogUiState())
    val uiState: StateFlow<ActivityLogUiState> = _ui.asStateFlow()

    private val users: StateFlow<List<User>> = userRepository.observeActiveUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Compose users + role state into the static parts of UiState.
        viewModelScope.launch {
            combine(users, sessionManager.currentUser) { list, current ->
                val isStaff = current?.role == UserRole.STAFF
                val isAdmin = current?.role == UserRole.ADMIN
                ActivityLogUiState(
                    users = list,
                    userNamesById = list.associate { it.id to it.name },
                    userFilterLocked = isStaff,
                    canSelectUser = !isStaff,
                    canExport = isAdmin,
                )
            }.collect { snapshot ->
                _ui.update {
                    it.copy(
                        users = snapshot.users,
                        userNamesById = snapshot.userNamesById,
                        userFilterLocked = snapshot.userFilterLocked,
                        canSelectUser = snapshot.canSelectUser,
                        canExport = snapshot.canExport,
                    )
                }
            }
        }
        // Reactively pull logs whenever the filter or current user changes.
        viewModelScope.launch {
            combine(_filter, sessionManager.currentUser) { f, current -> f to current }
                .flatMapLatest { (f, current) ->
                    getLogs(
                        actor = current,
                        userIdFilter = f.userId,
                        action = f.action,
                        fromMillis = f.startMillis,
                        toMillis = f.endMillis,
                    )
                }
                .collect { logs -> _ui.update { it.copy(logs = logs) } }
        }
    }

    fun setUserFilter(userId: String?) {
        if (_ui.value.userFilterLocked) return
        _filter.update { it.copy(userId = userId) }
    }

    fun setActionFilter(action: ActivityAction?) {
        _filter.update { it.copy(action = action) }
    }

    fun setStartDate(millis: Long) {
        _filter.update { it.copy(startMillis = startOfDay(millis)) }
    }

    fun setEndDate(millis: Long) {
        _filter.update { it.copy(endMillis = endOfDay(millis)) }
    }

    fun consumeMessages() {
        _ui.update { it.copy(infoMessage = null, errorMessage = null) }
    }

    fun export() {
        if (!_ui.value.canExport || _ui.value.isWorking) return
        val f = _filter.value
        val actorId = sessionManager.currentUser.value?.id ?: return
        _ui.update { it.copy(isWorking = true, infoMessage = null, errorMessage = null) }
        viewModelScope.launch {
            when (val r = exportLogs(f.startMillis, f.endMillis, actorId, f.userId)) {
                is ExportActivityLogUseCase.Result.Success -> {
                    val msg = "Exported ${r.rowCount} rows → ${r.fileName}"
                    _ui.update { it.copy(isWorking = false, infoMessage = msg) }
                    snackbarController.showSuccess(msg)
                }
                ExportActivityLogUseCase.Result.NoRecords ->
                    _ui.update {
                        it.copy(isWorking = false, errorMessage = "No log entries in that range")
                    }
                ExportActivityLogUseCase.Result.NotAuthorized ->
                    _ui.update {
                        it.copy(isWorking = false, errorMessage = "Only an admin can export the activity log")
                    }
                is ExportActivityLogUseCase.Result.WriteFailed ->
                    _ui.update {
                        it.copy(isWorking = false, errorMessage = "Export failed: ${r.message}")
                    }
            }
        }
    }
}

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 23)
    set(Calendar.MINUTE, 59)
    set(Calendar.SECOND, 59)
    set(Calendar.MILLISECOND, 999)
}.timeInMillis

private fun startOfNDaysAgo(days: Int): Long = Calendar.getInstance().apply {
    add(Calendar.DAY_OF_YEAR, -days)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
