package com.ykfj.inventory.ui.paluwagan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.PaluwaganGroup
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.usecase.paluwagan.CreatePaluwaganGroupUseCase
import com.ykfj.inventory.domain.usecase.paluwagan.GetActivePaluwaganGroupsUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PaluwaganGroupRow(
    val id: String,
    val name: String,
    val contributionLabel: String,
    val currentRound: Int,
    val totalSlots: Int,
    val status: String,
    val startDate: Long,
    val frequencyDays: Int,
    /** Name of the member whose slot position == currentRound, null if not started or not found. */
    val collectorName: String?,
)

data class PaluwaganUiState(
    val groups: List<PaluwaganGroupRow> = emptyList(),
    val isLoading: Boolean = true,
    val canManage: Boolean = false,
    val isAdmin: Boolean = false,
    val showCompleted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class PaluwaganViewModel @Inject constructor(
    private val getActiveGroups: GetActivePaluwaganGroupsUseCase,
    private val createGroup: CreatePaluwaganGroupUseCase,
    private val paluwaganRepository: PaluwaganRepository,
    private val customerRepository: CustomerRepository,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val _groupRows = MutableStateFlow<List<PaluwaganGroupRow>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _showCompleted = MutableStateFlow(false)

    val uiState: StateFlow<PaluwaganUiState> = combine(
        _groupRows,
        _isLoading,
        _error,
        sessionManager.currentUser,
        _showCompleted,
    ) { rows, isLoading, error, user, showCompleted ->
        PaluwaganUiState(
            groups = rows,
            isLoading = isLoading,
            canManage = user?.role in listOf(UserRole.ADMIN, UserRole.MANAGER),
            isAdmin = user?.role == UserRole.ADMIN,
            showCompleted = showCompleted,
            error = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PaluwaganUiState(),
    )

    /**
     * Paginated completed groups (10 per page). Only subscribe when the
     * completed tab is visible — `cachedIn` ensures the pager survives
     * recomposition without re-querying the DB.
     */
    val completedGroups: Flow<PagingData<PaluwaganGroupRow>> =
        paluwaganRepository.completedGroupsPaged()
            .map { pagingData ->
                pagingData.map { group -> group.toCompletedRow() }
            }
            .cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            getActiveGroups().collect { groups ->
                _groupRows.value = groups.map { group -> group.toRow() }
                _isLoading.value = false
            }
        }
        // Auto-purge completed groups older than 30 days on launch.
        viewModelScope.launch {
            val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1_000
            runCatching { paluwaganRepository.purgeCompletedOlderThan(cutoff) }
        }
    }

    fun toggleShowCompleted() {
        _showCompleted.value = !_showCompleted.value
    }

    fun createGroup(
        name: String,
        contributionAmount: Double,
        frequencyDays: Int,
        totalSlots: Int,
        startDate: Long,
        notes: String?,
    ) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            runCatching {
                createGroup(
                    CreatePaluwaganGroupUseCase.Params(
                        name = name,
                        contributionAmount = contributionAmount,
                        frequencyDays = frequencyDays,
                        totalSlots = totalSlots,
                        startDate = startDate,
                        notes = notes,
                        actorUserId = userId,
                    ),
                )
            }.onSuccess {
                snackbarController.showSuccess("Paluwagan group \"$name\" created")
            }.onFailure { e ->
                _error.value = e.message ?: "Failed to create group"
            }
        }
    }

    fun hardDeleteGroup(groupId: String) {
        viewModelScope.launch {
            runCatching {
                paluwaganRepository.hardDeleteGroup(groupId)
            }.onSuccess {
                snackbarController.showSuccess("Paluwagan group deleted")
            }.onFailure { _error.value = it.message ?: "Failed to delete group" }
        }
    }

    fun clearError() {
        _error.value = null
    }

    private suspend fun PaluwaganGroup.toRow(): PaluwaganGroupRow {
        val collectorName = if (currentRound > 0) {
            val slots = paluwaganRepository.getSlotsForGroup(id)
            val slot = slots.find { it.position == currentRound }
            slot?.let { customerRepository.getById(it.customerId)?.name }
        } else null

        return PaluwaganGroupRow(
            id = id,
            name = name,
            contributionLabel = "${CurrencyFormatter.format(contributionAmount)} / every $frequencyDays days",
            currentRound = currentRound,
            totalSlots = totalSlots,
            status = status.name.lowercase().replaceFirstChar { it.uppercase() },
            startDate = startDate,
            frequencyDays = frequencyDays,
            collectorName = collectorName,
        )
    }

    /** Completed groups don't need an async collector lookup. */
    private fun PaluwaganGroup.toCompletedRow() = PaluwaganGroupRow(
        id = id,
        name = name,
        contributionLabel = "${CurrencyFormatter.format(contributionAmount)} / every $frequencyDays days",
        currentRound = currentRound,
        totalSlots = totalSlots,
        status = status.name.lowercase().replaceFirstChar { it.uppercase() },
        startDate = startDate,
        frequencyDays = frequencyDays,
        collectorName = null,
    )
}
