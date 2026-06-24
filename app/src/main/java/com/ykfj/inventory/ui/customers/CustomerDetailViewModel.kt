package com.ykfj.inventory.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.model.LayawayTransaction
import com.ykfj.inventory.domain.model.PaluwaganPayment
import com.ykfj.inventory.domain.model.PaluwaganSlot
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomerLayawaySummary(
    val id: String,
    val productName: String,
    val status: LayawayStatus,
    val total: Double,
    val totalPaid: Double,
    val dueDate: Long?,
    val completionDate: Long?,
    val forfeitedAmount: Double?,
    val transactions: List<LayawayTransaction> = emptyList(),
) {
    val remaining: Double get() = total - totalPaid
    val onTimeCount: Int get() = transactions.count { dueDate == null || it.paymentDate <= dueDate }
    val lateCount: Int get() = transactions.count { dueDate != null && it.paymentDate > dueDate }
}

data class CustomerPaluwaganEntry(
    val groupId: String,
    val groupName: String,
    val contributionAmount: Double,
    val frequencyDays: Int,
    val totalRounds: Int,
    val currentRound: Int,
    val groupStatus: PaluwaganGroupStatus,
    val slotId: String,
    val position: Int,
    val payments: List<PaluwaganPayment>,
) {
    val paidCount: Int get() = payments.count {
        it.status == PaluwaganPaymentStatus.PAID || it.status == PaluwaganPaymentStatus.PREPAID
    }
    val lateCount: Int get() = payments.count { it.status == PaluwaganPaymentStatus.LATE }
}

data class CustomerDetailUiState(
    val customer: Customer? = null,
    val layaways: List<CustomerLayawaySummary> = emptyList(),
    val paluwaganEntries: List<CustomerPaluwaganEntry> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CustomerDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val customerRepository: CustomerRepository,
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val paluwaganRepository: PaluwaganRepository,
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val _state = MutableStateFlow(CustomerDetailUiState())

    val uiState: StateFlow<CustomerDetailUiState> = _state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CustomerDetailUiState(),
        )

    init {
        viewModelScope.launch {
            val customer = customerRepository.getById(customerId)
            _state.value = _state.value.copy(customer = customer)
        }

        viewModelScope.launch {
            layawayRepository.observeForCustomer(customerId)
                .flatMapLatest { records ->
                    if (records.isEmpty()) return@flatMapLatest flowOf(
                        emptyList<Pair<LayawayRecord, List<LayawayTransaction>>>()
                    )
                    val txFlows = records.map { record ->
                        layawayRepository.observeTransactions(record.id)
                            .map { txs -> record to txs }
                    }
                    combine(txFlows) { it.toList() }
                }
                .collect { recordTxPairs ->
                    val productMap = productRepository
                        .getByIds(recordTxPairs.map { it.first.productId }.distinct())
                        .associateBy { it.id }
                    val summaries = recordTxPairs.map { (r, txs) ->
                        CustomerLayawaySummary(
                            id = r.id,
                            productName = productMap[r.productId]?.name ?: r.productId,
                            status = r.status,
                            total = r.unitPrice * r.quantity,
                            totalPaid = r.totalPaid,
                            dueDate = r.dueDate,
                            completionDate = r.completionDate,
                            forfeitedAmount = r.forfeitedAmount,
                            transactions = txs,
                        )
                    }
                    _state.value = _state.value.copy(layaways = summaries, isLoading = false)
                }
        }

        viewModelScope.launch {
            paluwaganRepository.observeSlotsForCustomer(customerId)
                .flatMapLatest { slots ->
                    if (slots.isEmpty()) return@flatMapLatest flowOf(
                        emptyList<Pair<PaluwaganSlot, List<PaluwaganPayment>>>()
                    )
                    val paymentFlows = slots.map { slot ->
                        paluwaganRepository.observePaymentsForSlot(slot.id)
                            .map { payments -> slot to payments }
                    }
                    combine(paymentFlows) { it.toList() }
                }
                .collect { slotPaymentPairs ->
                    val groupMap = paluwaganRepository
                        .getGroupsByIds(slotPaymentPairs.map { it.first.groupId }.distinct())
                        .associateBy { it.id }
                    val entries = slotPaymentPairs.mapNotNull { (slot, payments) ->
                        val group = groupMap[slot.groupId] ?: return@mapNotNull null
                        CustomerPaluwaganEntry(
                            groupId = group.id,
                            groupName = group.name,
                            contributionAmount = group.contributionAmount,
                            frequencyDays = group.frequencyDays,
                            totalRounds = group.totalSlots,
                            currentRound = group.currentRound,
                            groupStatus = group.status,
                            slotId = slot.id,
                            position = slot.position,
                            payments = payments.sortedBy { it.roundNumber },
                        )
                    }.sortedBy { it.groupName }
                    _state.value = _state.value.copy(paluwaganEntries = entries)
                }
        }
    }
}
