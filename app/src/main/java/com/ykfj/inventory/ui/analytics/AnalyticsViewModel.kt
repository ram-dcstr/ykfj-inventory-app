package com.ykfj.inventory.ui.analytics

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.domain.model.GoldTradingSummary
import com.ykfj.inventory.domain.model.InventorySummary
import com.ykfj.inventory.domain.model.SalesSummary
import com.ykfj.inventory.domain.usecase.analytics.ExportSalesUseCase
import com.ykfj.inventory.domain.usecase.analytics.GetDailySalesUseCase
import com.ykfj.inventory.domain.usecase.analytics.GetGoldTradingSummaryUseCase
import com.ykfj.inventory.domain.usecase.analytics.GetInventorySummaryUseCase
import com.ykfj.inventory.domain.usecase.analytics.GetMonthlySalesUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class AnalyticsUiState(
    val dailySummary: SalesSummary = SalesSummary.Empty,
    val monthlySummary: SalesSummary = SalesSummary.Empty,
    val inventorySummary: InventorySummary = InventorySummary.Empty,
    val dailyGoldTrading: GoldTradingSummary = GoldTradingSummary.Empty,
    val monthlyGoldTrading: GoldTradingSummary = GoldTradingSummary.Empty,
    val selectedDayMillis: Long = 0L,
    val selectedYear: Int = 0,
    val selectedMonth: Int = 0,
    val isExporting: Boolean = false,
    val exportedUri: Uri? = null,
    val exportError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    getDailySales: GetDailySalesUseCase,
    getMonthlySales: GetMonthlySalesUseCase,
    getInventorySummary: GetInventorySummaryUseCase,
    getGoldTradingSummary: GetGoldTradingSummaryUseCase,
    private val exportSales: ExportSalesUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val _selectedDayMillis = MutableStateFlow(todayStartMillis())
    private val _selectedMonthKey = MutableStateFlow(Pair(currentYear(), currentMonth()))
    private val _exportState = MutableStateFlow(ExportState())

    private val dailyFlow = _selectedDayMillis.flatMapLatest { start ->
        getDailySales(start, start + DAY_MILLIS - 1)
    }

    private val monthlyFlow = _selectedMonthKey.flatMapLatest { (year, month) ->
        getMonthlySales(monthStartMillis(year, month), monthEndMillis(year, month))
    }

    private val dailyGoldFlow = _selectedDayMillis.flatMapLatest { start ->
        getGoldTradingSummary(start, start + DAY_MILLIS - 1)
    }

    private val monthlyGoldFlow = _selectedMonthKey.flatMapLatest { (year, month) ->
        getGoldTradingSummary(monthStartMillis(year, month), monthEndMillis(year, month))
    }

    val uiState = combine(
        dailyFlow,
        monthlyFlow,
        getInventorySummary(),
        dailyGoldFlow,
        monthlyGoldFlow,
        _selectedDayMillis,
        _selectedMonthKey,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val daily = values[0] as SalesSummary
        @Suppress("UNCHECKED_CAST")
        val monthly = values[1] as SalesSummary
        @Suppress("UNCHECKED_CAST")
        val inventory = values[2] as InventorySummary
        @Suppress("UNCHECKED_CAST")
        val dailyGold = values[3] as GoldTradingSummary
        @Suppress("UNCHECKED_CAST")
        val monthlyGold = values[4] as GoldTradingSummary
        val dayMillis = values[5] as Long
        @Suppress("UNCHECKED_CAST")
        val monthKey = values[6] as Pair<Int, Int>
        AnalyticsUiState(
            dailySummary = daily,
            monthlySummary = monthly,
            inventorySummary = inventory,
            dailyGoldTrading = dailyGold,
            monthlyGoldTrading = monthlyGold,
            selectedDayMillis = dayMillis,
            selectedYear = monthKey.first,
            selectedMonth = monthKey.second,
        )
    }.combine(_exportState) { base, export ->
        base.copy(
            isExporting = export.isExporting,
            exportedUri = export.uri,
            exportError = export.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState())

    fun selectDay(millis: Long) { _selectedDayMillis.value = startOfDay(millis) }

    fun selectMonth(year: Int, month: Int) { _selectedMonthKey.value = Pair(year, month) }

    /** Snaps the daily and monthly filters back to the current day/month. */
    fun resetToToday() {
        _selectedDayMillis.value = todayStartMillis()
        _selectedMonthKey.value = Pair(currentYear(), currentMonth())
    }

    fun exportDayCsv() {
        val day = _selectedDayMillis.value
        export(start = day, end = day + DAY_MILLIS - 1)
    }

    fun exportMonthCsv() {
        val (year, month) = _selectedMonthKey.value
        export(start = monthStartMillis(year, month), end = monthEndMillis(year, month))
    }

    fun clearExport() { _exportState.update { ExportState() } }

    private fun export(start: Long, end: Long) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            _exportState.update { ExportState(isExporting = true) }
            when (val result = exportSales(ExportSalesUseCase.Params(start, end, userId))) {
                is ExportSalesUseCase.Result.Success -> {
                    _exportState.update { ExportState(uri = result.uri) }
                    // AnalyticsScreen's LaunchedEffect on exportedUri fires a share
                    // intent right after this — don't say "open from Downloads"
                    // because the user gets an "Open CSV with…" picker immediately.
                    snackbarController.showSuccess("Sales CSV exported")
                }
                is ExportSalesUseCase.Result.Error ->
                    _exportState.update { ExportState(error = result.message) }
            }
        }
    }

    private data class ExportState(
        val isExporting: Boolean = false,
        val uri: Uri? = null,
        val error: String? = null,
    )

    companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L

        fun todayStartMillis(): Long = startOfDay(System.currentTimeMillis())

        fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
        fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH)

        fun monthStartMillis(year: Int, month: Int): Long = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        fun monthEndMillis(year: Int, month: Int): Long = Calendar.getInstance().apply {
            set(year, month, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1)
        }.timeInMillis
    }
}
