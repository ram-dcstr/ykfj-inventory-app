package com.ykfj.inventory.ui.layaway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.layaway.GetActiveLayawaysUseCase
import com.ykfj.inventory.domain.usecase.layaway.GetCompletedLayawaysUseCase
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

data class LayawayRow(
    val id: String,
    val productId: String,
    val productName: String,
    val customerId: String,
    val customerName: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPaid: Double,
    val dueDate: Long?,
    val createdAt: Long,
    val status: LayawayStatus,
    val completionDate: Long?,
) {
    val total: Double get() = unitPrice * quantity
    val remaining: Double get() = total - totalPaid
    /** Currently overdue — only meaningful for ACTIVE layaways. */
    val isOverdue: Boolean
        get() = status == LayawayStatus.ACTIVE &&
            dueDate != null && System.currentTimeMillis() > dueDate
    /** Completed past its due date — historical "paid late" flag. */
    val wasPaidLate: Boolean
        get() = status == LayawayStatus.COMPLETED &&
            dueDate != null && completionDate != null && completionDate > dueDate
}

/** All layaways for one customer (in the current filter) grouped together. */
data class CustomerLayawayGroup(
    val customerId: String,
    val customerName: String,
    val layaways: List<LayawayRow>,
) {
    val totalAmount: Double get() = layaways.sumOf { it.total }
    val totalPaid: Double get() = layaways.sumOf { it.totalPaid }
    val totalRemaining: Double get() = layaways.sumOf { it.remaining }
    val isOverdue: Boolean get() = layaways.any { it.isOverdue }
    val latePaymentCount: Int get() = layaways.count { it.wasPaidLate }
}

enum class LayawayFilter { Active, Completed }

data class LayawayListUiState(
    val groups: List<CustomerLayawayGroup> = emptyList(),
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val filter: LayawayFilter = LayawayFilter.Active,
    val error: String? = null,
) {
    val filteredGroups: List<CustomerLayawayGroup>
        get() = if (searchQuery.isBlank()) groups
        else groups.filter {
            it.customerName.contains(searchQuery, ignoreCase = true) ||
                it.layaways.any { r -> r.productName.contains(searchQuery, ignoreCase = true) }
        }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LayawayViewModel @Inject constructor(
    private val getActiveLayaways: GetActiveLayawaysUseCase,
    private val getCompletedLayaways: GetCompletedLayawaysUseCase,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _filter = MutableStateFlow(LayawayFilter.Active)
    private val _pageState = MutableStateFlow(PageState())

    val uiState: StateFlow<LayawayListUiState> = combine(
        _pageState,
        _searchQuery,
        _filter,
    ) { page, query, filter ->
        LayawayListUiState(
            groups = page.groups,
            isLoading = page.isLoading,
            searchQuery = query,
            filter = filter,
            error = page.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LayawayListUiState(),
    )

    init {
        viewModelScope.launch {
            _filter
                .flatMapLatest { filter ->
                    when (filter) {
                        LayawayFilter.Active -> getActiveLayaways()
                        LayawayFilter.Completed -> getCompletedLayaways()
                    }
                }
                .mapLatest { records -> buildGroups(records) }
                .collect { groups ->
                    _pageState.value = _pageState.value.copy(groups = groups, isLoading = false)
                }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: LayawayFilter) {
        if (_filter.value == filter) return
        _pageState.value = _pageState.value.copy(isLoading = true)
        _filter.value = filter
    }

    fun clearError() {
        _pageState.value = _pageState.value.copy(error = null)
    }

    private suspend fun buildGroups(records: List<LayawayRecord>): List<CustomerLayawayGroup> {
        if (records.isEmpty()) return emptyList()
        val productMap = records.map { it.productId }.distinct()
            .associateWith { productRepository.getById(it) }
        val customerMap = records.map { it.customerId }.distinct()
            .associateWith { customerRepository.getById(it) }

        return records
            .groupBy { it.customerId }
            .map { (customerId, customerRecords) ->
                val customerName = customerMap[customerId]?.name ?: customerId
                CustomerLayawayGroup(
                    customerId = customerId,
                    customerName = customerName,
                    layaways = customerRecords.map { record ->
                        LayawayRow(
                            id = record.id,
                            productId = record.productId,
                            productName = productMap[record.productId]?.name ?: record.productId,
                            customerId = record.customerId,
                            customerName = customerName,
                            quantity = record.quantity,
                            unitPrice = record.unitPrice,
                            totalPaid = record.totalPaid,
                            dueDate = record.dueDate,
                            createdAt = record.createdAt,
                            status = record.status,
                            completionDate = record.completionDate,
                        )
                    },
                )
            }
            .sortedByDescending { it.isOverdue }
    }

    private data class PageState(
        val groups: List<CustomerLayawayGroup> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )
}
