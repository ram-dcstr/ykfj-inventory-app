package com.ykfj.inventory.ui.sold

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.SoldRecord
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.sold.ExportDailySalesPdfUseCase
import com.ykfj.inventory.domain.usecase.sold.GetSoldRecordsUseCase
import com.ykfj.inventory.domain.usecase.sold.RevertSoldUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SoldRecordRow(
    val id: String,
    val productId: String,
    val productName: String,
    val weightGrams: Double?,
    val size: String?,
    val customerName: String?,
    val quantity: Int,
    val soldPricePerUnit: Double,
    val capitalPricePerUnit: Double,
    val discountAmount: Double,
    val discountType: DiscountType,
    val soldDate: Long,
    val notes: String?,
) {
    val totalRevenue: Double get() = soldPricePerUnit * quantity
    val totalCapital: Double get() = capitalPricePerUnit * quantity
    val profit: Double get() = totalRevenue - totalCapital
}

data class SoldArchiveUiState(
    val selectedDateMillis: Long = GetSoldRecordsUseCase.startOfDay(System.currentTimeMillis()),
    val records: List<SoldRecordRow> = emptyList(),
    val isLoading: Boolean = true,
    val totalRevenue: Double = 0.0,
    val totalCapital: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalItems: Int = 0,
    val canRevert: Boolean = false,
    val isExporting: Boolean = false,
    val exportedFilename: String? = null,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SoldArchiveViewModel @Inject constructor(
    private val getSoldRecords: GetSoldRecordsUseCase,
    private val revertSold: RevertSoldUseCase,
    private val exportPdf: ExportDailySalesPdfUseCase,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(
        GetSoldRecordsUseCase.startOfDay(System.currentTimeMillis()),
    )
    private val _pageState = MutableStateFlow(PageState())

    val uiState: StateFlow<SoldArchiveUiState> = combine(
        _selectedDate,
        _pageState,
        sessionManager.currentUser,
    ) { date, page, user ->
        val canRevert = user?.role in listOf(UserRole.ADMIN, UserRole.MANAGER)
        SoldArchiveUiState(
            selectedDateMillis = date,
            records = page.rows,
            isLoading = page.isLoading,
            totalRevenue = page.rows.sumOf { it.totalRevenue },
            totalCapital = page.rows.sumOf { it.totalCapital },
            totalProfit = page.rows.sumOf { it.profit },
            totalItems = page.rows.sumOf { it.quantity },
            canRevert = canRevert,
            isExporting = page.isExporting,
            exportedFilename = page.exportedFilename,
            error = page.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SoldArchiveUiState(),
    )

    init {
        viewModelScope.launch {
            _selectedDate
                .onEach { _pageState.value = _pageState.value.copy(isLoading = true, rows = emptyList()) }
                .flatMapLatest { date -> getSoldRecords(date) }
                .mapLatest { records -> enrichRecords(records) }
                .collect { rows ->
                    _pageState.value = _pageState.value.copy(rows = rows, isLoading = false)
                }
        }
    }

    fun selectDate(dateMillis: Long) {
        _selectedDate.value = GetSoldRecordsUseCase.startOfDay(dateMillis)
    }

    /** Snaps the date filter back to the current day. */
    fun resetToToday() {
        selectDate(System.currentTimeMillis())
    }

    fun clearError() {
        _pageState.value = _pageState.value.copy(error = null)
    }

    fun clearExported() {
        _pageState.value = _pageState.value.copy(exportedFilename = null)
    }

    fun revert(soldId: String, quantity: Int, reason: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val result = revertSold(RevertSoldUseCase.Params(soldId, quantity, reason, userId))) {
                RevertSoldUseCase.Result.Success -> {
                    snackbarController.showSuccess(
                        "Reverted $quantity unit${if (quantity == 1) "" else "s"} · stock restored",
                    )
                    /* Room Flow auto-refreshes the list */
                }
                RevertSoldUseCase.Result.RecordNotFound ->
                    _pageState.value = _pageState.value.copy(error = "Record not found")
                is RevertSoldUseCase.Result.Error ->
                    _pageState.value = _pageState.value.copy(error = result.message)
            }
        }
    }

    fun exportPdf() {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            _pageState.value = _pageState.value.copy(isExporting = true, error = null)
            when (val result = exportPdf(ExportDailySalesPdfUseCase.Params(_selectedDate.value, userId))) {
                is ExportDailySalesPdfUseCase.Result.Success -> {
                    _pageState.value = _pageState.value.copy(
                        isExporting = false,
                        exportedFilename = result.filename,
                    )
                    snackbarController.showSuccess("Daily sales PDF exported · ${result.filename}")
                }
                is ExportDailySalesPdfUseCase.Result.Error ->
                    _pageState.value = _pageState.value.copy(
                        isExporting = false,
                        error = result.message,
                    )
            }
        }
    }

    private suspend fun enrichRecords(records: List<SoldRecord>): List<SoldRecordRow> {
        if (records.isEmpty()) return emptyList()
        val productMap = productRepository.getByIds(records.map { it.productId }.distinct())
            .associateBy { it.id }
        val customerMap = customerRepository.getByIds(records.mapNotNull { it.customerId }.distinct())
            .associateBy { it.id }
        return records.map { record ->
            val product = productMap[record.productId]
            val customer = record.customerId?.let { customerMap[it] }
            SoldRecordRow(
                id = record.id,
                productId = record.productId,
                productName = product?.name ?: record.productId,
                weightGrams = product?.weightGrams,
                size = product?.size,
                customerName = customer?.name,
                quantity = record.quantity,
                soldPricePerUnit = record.soldPrice,
                capitalPricePerUnit = record.capitalPrice,
                discountAmount = record.discountAmount,
                discountType = record.discountType,
                soldDate = record.soldDate,
                notes = record.notes,
            )
        }
    }

    private data class PageState(
        val rows: List<SoldRecordRow> = emptyList(),
        val isLoading: Boolean = true,
        val isExporting: Boolean = false,
        val exportedFilename: String? = null,
        val error: String? = null,
    )
}
