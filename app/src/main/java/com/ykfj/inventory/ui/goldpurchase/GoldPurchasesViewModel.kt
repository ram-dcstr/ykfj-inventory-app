package com.ykfj.inventory.ui.goldpurchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.usecase.goldpurchase.MarkGoldSoldToSupplierUseCase
import com.ykfj.inventory.domain.usecase.goldpurchase.RevertGoldPurchaseUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Per-item row shown on the list screen — each gold-purchase item appears as
 * its own card. [recordId] is the parent purchase record (used for navigation).
 */
data class GoldPurchaseItemRow(
    val itemId: String,
    val recordId: String,
    val description: String,
    val purity: String?,
    val weightGrams: Double,
    val finalValue: Double,
    val customerName: String?,
    val paidAt: Long,
    val isTradeIn: Boolean,
    val dateLabel: String,
    val isSoldToSupplier: Boolean,
    val supplierProfit: Double?,
    /** True if any item in this row's parent record has been sold to supplier. */
    val parentHasSoldItems: Boolean,
)

/** Single-select filter applied above the list. Hides chips with zero matching items. */
sealed class GoldPurchaseFilter {
    object All : GoldPurchaseFilter()
    object InStock : GoldPurchaseFilter()
    object Sold : GoldPurchaseFilter()
    data class Purity(val value: String) : GoldPurchaseFilter()
}

data class GoldPurchasesUiState(
    val rows: List<GoldPurchaseItemRow> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val canDelete: Boolean = false,
    val deleteError: String? = null,
    val filter: GoldPurchaseFilter = GoldPurchaseFilter.All,
    val availablePurities: List<String> = emptyList(),
    val selectedItemIds: Set<String> = emptySet(),
    val bulkError: String? = null,
) {
    val isInSelectMode: Boolean get() = selectedItemIds.isNotEmpty()

    val filtered: List<GoldPurchaseItemRow>
        get() {
            val byFilter = when (val f = filter) {
                GoldPurchaseFilter.All -> rows
                GoldPurchaseFilter.InStock -> rows.filter { !it.isSoldToSupplier }
                GoldPurchaseFilter.Sold -> rows.filter { it.isSoldToSupplier }
                is GoldPurchaseFilter.Purity -> rows.filter { it.purity == f.value }
            }
            if (searchQuery.isBlank()) return byFilter
            val q = searchQuery.lowercase()
            return byFilter.filter {
                it.description.lowercase().contains(q) ||
                    (it.customerName ?: "Walk-in").lowercase().contains(q) ||
                    (it.purity?.lowercase()?.contains(q) ?: false) ||
                    it.dateLabel.lowercase().contains(q)
            }
        }

    /** Items currently selected and eligible for bulk sell-to-supplier (skip already-sold). */
    val selectedSellableRows: List<GoldPurchaseItemRow>
        get() = rows.filter { it.itemId in selectedItemIds && !it.isSoldToSupplier }

    val selectedTotalWeight: Double get() = selectedSellableRows.sumOf { it.weightGrams }
    val selectedTotalPaid: Double get() = selectedSellableRows.sumOf { it.finalValue }
}

@HiltViewModel
class GoldPurchasesViewModel @Inject constructor(
    private val goldPurchaseRepository: GoldPurchaseRepository,
    private val customerRepository: CustomerRepository,
    private val revertUseCase: RevertGoldPurchaseUseCase,
    private val markSoldUseCase: MarkGoldSoldToSupplierUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val dateSdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val _searchQuery = MutableStateFlow("")
    private val _rows = MutableStateFlow<List<GoldPurchaseItemRow>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _deleteError = MutableStateFlow<String?>(null)
    private val _filter = MutableStateFlow<GoldPurchaseFilter>(GoldPurchaseFilter.All)
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    private val _bulkError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<GoldPurchasesUiState> = combine(
        listOf(
            _rows, _searchQuery, _isLoading, sessionManager.currentUser,
            _deleteError, _filter, _selected, _bulkError,
        ),
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val rows = values[0] as List<GoldPurchaseItemRow>
        val query = values[1] as String
        val loading = values[2] as Boolean
        val user = values[3] as com.ykfj.inventory.domain.model.User?
        val deleteError = values[4] as String?
        val filter = values[5] as GoldPurchaseFilter
        @Suppress("UNCHECKED_CAST")
        val selected = values[6] as Set<String>
        val bulkError = values[7] as String?
        GoldPurchasesUiState(
            rows = rows,
            searchQuery = query,
            isLoading = loading,
            canDelete = user?.role == UserRole.ADMIN,
            deleteError = deleteError,
            filter = filter,
            availablePurities = rows.mapNotNull { it.purity }.distinct().sorted(),
            selectedItemIds = selected,
            bulkError = bulkError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoldPurchasesUiState(),
    )

    init {
        combine(
            goldPurchaseRepository.observeAll(),
            goldPurchaseRepository.observeAllItems(),
            customerRepository.observeAll(),
        ) { records, items, customers ->
            val recordsById = records.associateBy { it.id }
            val customersById = customers.associateBy { it.id }
            val recordsWithSoldItems = items
                .filter { it.isSoldToSupplier }
                .mapTo(hashSetOf()) { it.purchaseRecordId }
            items
                .mapNotNull { item ->
                    val record = recordsById[item.purchaseRecordId] ?: return@mapNotNull null
                    GoldPurchaseItemRow(
                        itemId = item.id,
                        recordId = record.id,
                        description = item.description,
                        purity = item.purity,
                        weightGrams = item.weightGrams,
                        finalValue = item.finalValue,
                        customerName = record.customerId?.let { customersById[it]?.name },
                        paidAt = record.paidAt,
                        isTradeIn = record.linkedSoldRecordId != null,
                        dateLabel = dateSdf.format(Date(record.paidAt)),
                        isSoldToSupplier = item.isSoldToSupplier,
                        supplierProfit = item.profitFromSupplier,
                        parentHasSoldItems = record.id in recordsWithSoldItems,
                    )
                }
                .sortedByDescending { it.paidAt }
        }
            .onEach { rows ->
                _rows.value = rows
                _isLoading.value = false
                // Drop selections that no longer exist (e.g. record was deleted).
                val validIds = rows.mapTo(hashSetOf()) { it.itemId }
                _selected.value = _selected.value.intersect(validIds)
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: GoldPurchaseFilter) {
        _filter.value = filter
    }

    fun toggleSelection(itemId: String) {
        _selected.value = _selected.value.toMutableSet().apply {
            if (!add(itemId)) remove(itemId)
        }
    }

    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun clearBulkError() {
        _bulkError.value = null
    }

    /**
     * Marks every selected (and not-yet-sold) item sold to the supplier at [pricePerGram].
     * Per-item total = weight × pricePerGram.
     */
    fun bulkSellToSupplier(pricePerGram: Double) {
        val userId = sessionManager.currentUser.value?.id ?: return
        if (pricePerGram <= 0) {
            _bulkError.value = "Per-gram rate must be greater than 0"
            return
        }
        val targets = uiState.value.selectedSellableRows
        if (targets.isEmpty()) {
            _bulkError.value = "No items to sell"
            return
        }
        viewModelScope.launch {
            var failures = 0
            targets.forEach { row ->
                val total = row.weightGrams * pricePerGram
                val result = markSoldUseCase(
                    MarkGoldSoldToSupplierUseCase.Params(row.itemId, total, userId),
                )
                if (result !is MarkGoldSoldToSupplierUseCase.Result.Success) failures++
            }
            _selected.value = emptySet()
            if (failures > 0) {
                _bulkError.value = "Failed to mark $failures item(s) sold"
            }
        }
    }

    /** Soft-deletes the parent purchase record (Admin-only — UI hides the affordance otherwise). */
    fun deletePurchase(recordId: String, reason: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val result = revertUseCase(
                RevertGoldPurchaseUseCase.Params(recordId, reason, userId),
            )) {
                RevertGoldPurchaseUseCase.Result.Success -> Unit
                RevertGoldPurchaseUseCase.Result.NotFound ->
                    _deleteError.value = "Purchase not found"
                RevertGoldPurchaseUseCase.Result.IsTradeIn ->
                    _deleteError.value = "Trade-in purchases must be reverted from the trade-in screen"
                is RevertGoldPurchaseUseCase.Result.Error ->
                    _deleteError.value = result.message
            }
        }
    }

    fun clearDeleteError() {
        _deleteError.value = null
    }
}
