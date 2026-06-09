package com.ykfj.inventory.ui.damaged

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.DamagedRecord
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.damaged.GetDamagedRecordsUseCase
import com.ykfj.inventory.domain.usecase.damaged.GetMeltedRecordsUseCase
import com.ykfj.inventory.domain.usecase.damaged.MeltDamagedProductUseCase
import com.ykfj.inventory.domain.usecase.damaged.RevertDamagedUseCase
import com.ykfj.inventory.domain.usecase.damaged.RevertMeltUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DamagedRecordRow(
    val id: String,
    val productId: String,
    val productName: String,
    val reason: String,
    val dateRecorded: Long,
    val notes: String?,
    val isMelted: Boolean = false,
)

enum class DamagedFilter { Active, Melted }

data class DamagedUiState(
    val records: List<DamagedRecordRow> = emptyList(),
    val isLoading: Boolean = true,
    val canRevert: Boolean = false,
    /** Reverting a melt is admin-only — un-melts the product itself, not just the record. */
    val canRevertMelt: Boolean = false,
    val filter: DamagedFilter = DamagedFilter.Active,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DamagedViewModel @Inject constructor(
    private val getDamagedRecords: GetDamagedRecordsUseCase,
    private val getMeltedRecords: GetMeltedRecordsUseCase,
    private val revertDamaged: RevertDamagedUseCase,
    private val meltDamagedProduct: MeltDamagedProductUseCase,
    private val revertMeltUseCase: RevertMeltUseCase,
    private val productRepository: ProductRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _filter = MutableStateFlow(DamagedFilter.Active)
    private val _pageState = MutableStateFlow(PageState())

    val uiState: StateFlow<DamagedUiState> = combine(
        _pageState,
        _filter,
        sessionManager.currentUser,
    ) { page, filter, user ->
        DamagedUiState(
            records = page.rows,
            isLoading = page.isLoading,
            canRevert = user?.role in listOf(UserRole.ADMIN, UserRole.MANAGER),
            canRevertMelt = user?.role == UserRole.ADMIN,
            filter = filter,
            error = page.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DamagedUiState(),
    )

    init {
        viewModelScope.launch {
            _filter
                .flatMapLatest { filter ->
                    when (filter) {
                        DamagedFilter.Active -> getDamagedRecords()
                        DamagedFilter.Melted -> getMeltedRecords()
                    }
                }
                .mapLatest { records -> enrichRecords(records, _filter.value == DamagedFilter.Melted) }
                .collect { rows ->
                    _pageState.value = _pageState.value.copy(rows = rows, isLoading = false)
                }
        }
    }

    fun setFilter(filter: DamagedFilter) {
        if (_filter.value == filter) return
        _pageState.value = _pageState.value.copy(isLoading = true)
        _filter.value = filter
    }

    fun revert(damagedId: String, reason: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val result = revertDamaged(RevertDamagedUseCase.Params(damagedId, reason, userId))) {
                RevertDamagedUseCase.Result.Success -> { /* Room Flow auto-refreshes */ }
                RevertDamagedUseCase.Result.RecordNotFound ->
                    _pageState.value = _pageState.value.copy(error = "Record not found")
                is RevertDamagedUseCase.Result.Error ->
                    _pageState.value = _pageState.value.copy(error = result.message)
            }
        }
    }

    fun melt(damagedId: String, notes: String?) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (meltDamagedProduct(MeltDamagedProductUseCase.Params(damagedId, notes, userId))) {
                MeltDamagedProductUseCase.Result.Success -> { /* Room Flow auto-refreshes */ }
                MeltDamagedProductUseCase.Result.RecordNotFound ->
                    _pageState.value = _pageState.value.copy(error = "Record not found")
            }
        }
    }

    fun revertMelt(damagedId: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (revertMeltUseCase(RevertMeltUseCase.Params(damagedId, userId))) {
                RevertMeltUseCase.Result.Success -> { /* Room Flow auto-refreshes */ }
                RevertMeltUseCase.Result.RecordNotFound ->
                    _pageState.value = _pageState.value.copy(error = "Record not found")
            }
        }
    }

    fun clearError() {
        _pageState.value = _pageState.value.copy(error = null)
    }

    private suspend fun enrichRecords(
        records: List<DamagedRecord>,
        isMeltedView: Boolean,
    ): List<DamagedRecordRow> {
        if (records.isEmpty()) return emptyList()
        // Melted view needs the soft-deleted product row; the active view uses the
        // standard `getById` which filters them out (and shouldn't see them anyway).
        val productMap = records.map { it.productId }.distinct().associateWith { id ->
            if (isMeltedView) productRepository.getByIdAnyState(id)
            else productRepository.getById(id)
        }
        return records.map { record ->
            val product = productMap[record.productId]
            DamagedRecordRow(
                id = record.id,
                productId = record.productId,
                productName = product?.name ?: record.productId,
                reason = record.reason,
                dateRecorded = record.dateRecorded,
                notes = record.notes,
                isMelted = isMeltedView,
            )
        }
    }

    private data class PageState(
        val rows: List<DamagedRecordRow> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )
}
