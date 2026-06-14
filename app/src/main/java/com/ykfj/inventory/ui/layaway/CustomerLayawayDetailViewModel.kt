package com.ykfj.inventory.ui.layaway

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.model.LayawayTransaction
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.layaway.AddLayawayPaymentUseCase
import com.ykfj.inventory.domain.usecase.layaway.CancelLayawayUseCase
import com.ykfj.inventory.domain.usecase.layaway.CompleteLayawayUseCase
import com.ykfj.inventory.domain.usecase.layaway.DeleteLayawayPaymentUseCase
import com.ykfj.inventory.domain.usecase.layaway.RevertCompletedLayawayUseCase
import com.ykfj.inventory.domain.usecase.layaway.SplitLayawayPaymentUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LayawayEntryState(
    val record: LayawayRecord,
    val productName: String,
    val transactions: List<LayawayTransaction>,
) {
    val total: Double get() = record.unitPrice * record.quantity
    val remaining: Double get() = total - record.totalPaid
    val isFullyPaid: Boolean get() = record.totalPaid >= total
    val isCompleted: Boolean get() = record.status == LayawayStatus.COMPLETED
    val isActive: Boolean get() = record.status == LayawayStatus.ACTIVE
    /** Active, due date set, due date in the past — i.e. payment is currently overdue. */
    val isCurrentlyOverdue: Boolean
        get() = isActive && record.dueDate != null && System.currentTimeMillis() > record.dueDate
    /** Completed AFTER the due date — historical "paid late" indicator. */
    val wasPaidLate: Boolean
        get() = isCompleted && record.dueDate != null && record.completionDate != null &&
            record.completionDate > record.dueDate
}

data class CustomerLayawayDetailUiState(
    val customerId: String = "",
    val customerName: String = "",
    val entries: List<LayawayEntryState> = emptyList(),
    /** All logged-in roles — can add layaway payments per business rules. */
    val canManage: Boolean = false,
    /** Admin only — can complete/cancel/edit/delete payments. */
    val canAdmin: Boolean = false,
    val isLoading: Boolean = true,
    val success: String? = null,
    val error: String? = null,
) {
    val totalAmount: Double get() = entries.sumOf { it.total }
    val totalPaid: Double get() = entries.sumOf { it.record.totalPaid }
    val totalRemaining: Double get() = entries.sumOf { it.remaining }
    val allActive: List<LayawayEntryState> get() = entries.filter { it.record.status == LayawayStatus.ACTIVE }
}

@HiltViewModel
class CustomerLayawayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val addPaymentUseCase: AddLayawayPaymentUseCase,
    private val splitPaymentUseCase: SplitLayawayPaymentUseCase,
    private val completeLayawayUseCase: CompleteLayawayUseCase,
    private val cancelLayawayUseCase: CancelLayawayUseCase,
    private val deletePaymentUseCase: DeleteLayawayPaymentUseCase,
    private val revertCompletedLayawayUseCase: RevertCompletedLayawayUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: com.ykfj.inventory.ui.components.SnackbarController,
) : ViewModel() {

    private val customerId: String = checkNotNull(savedStateHandle["customerId"])
    private val _local = MutableStateFlow(LocalState())

    val uiState: StateFlow<CustomerLayawayDetailUiState> = combine(
        _local,
        sessionManager.currentUser,
    ) { local, user ->
        CustomerLayawayDetailUiState(
            customerId = customerId,
            customerName = local.customerName,
            entries = local.entries,
            canManage = user != null,
            canAdmin = user?.role == UserRole.ADMIN,
            isLoading = local.isLoading,
            success = local.success,
            error = local.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CustomerLayawayDetailUiState(customerId = customerId),
    )

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val customer = customerRepository.getById(customerId)
            _local.value = _local.value.copy(customerName = customer?.name ?: customerId)

            // Show all (non-deleted, non-archived) layaways for this customer — active and
            // completed alike. Filtering only to ACTIVE here previously made the Completed
            // filter on the list screen navigate into an empty detail view.
            layawayRepository.observeForCustomer(customerId)
                .collect { records ->
                    val productMap = records.map { it.productId }.distinct()
                        .associateWith { productRepository.getById(it) }

                    val entries = records.map { record ->
                        val transactions = layawayRepository.observeTransactions(record.id).first()
                        LayawayEntryState(
                            record = record,
                            productName = productMap[record.productId]?.name ?: record.productId,
                            transactions = transactions,
                        )
                    }
                    _local.value = _local.value.copy(entries = entries, isLoading = false)
                }
        }
    }

    fun addPayment(layawayId: String, amount: Double, paymentMethod: PaymentMethod = PaymentMethod.CASH, notes: String?) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = addPaymentUseCase(AddLayawayPaymentUseCase.Params(layawayId, amount, notes, userId, paymentMethod = paymentMethod))) {
                AddLayawayPaymentUseCase.Result.Success ->
                    snackbarController.showSuccess("Payment ${com.ykfj.inventory.util.CurrencyFormatter.format(amount)} added")
                AddLayawayPaymentUseCase.Result.RecordNotFound ->
                    _local.value = _local.value.copy(error = "Record not found")
                AddLayawayPaymentUseCase.Result.AlreadyCompleted ->
                    _local.value = _local.value.copy(error = "Layaway is already completed")
                is AddLayawayPaymentUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun splitPayment(allocations: List<SplitLayawayPaymentUseCase.Allocation>, paymentMethod: PaymentMethod = PaymentMethod.CASH) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = splitPaymentUseCase(
                SplitLayawayPaymentUseCase.Params(allocations, customerId, userId, paymentMethod = paymentMethod),
            )) {
                SplitLayawayPaymentUseCase.Result.Success ->
                    snackbarController.showSuccess("Split payment applied across ${allocations.size} layaways")
                is SplitLayawayPaymentUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun completeLayaway(layawayId: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = completeLayawayUseCase(CompleteLayawayUseCase.Params(layawayId, userId))) {
                CompleteLayawayUseCase.Result.Success ->
                    snackbarController.showSuccess("Layaway completed · moved to Sold archive")
                CompleteLayawayUseCase.Result.RecordNotFound ->
                    _local.value = _local.value.copy(error = "Record not found")
                CompleteLayawayUseCase.Result.NotActive ->
                    _local.value = _local.value.copy(error = "Layaway is not active")
                is CompleteLayawayUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun cancelLayaway(layawayId: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = cancelLayawayUseCase(CancelLayawayUseCase.Params(layawayId, userId))) {
                CancelLayawayUseCase.Result.Success ->
                    snackbarController.showSuccess("Layaway cancelled · product returned to inventory")
                CancelLayawayUseCase.Result.RecordNotFound ->
                    _local.value = _local.value.copy(error = "Record not found")
                CancelLayawayUseCase.Result.NotActive ->
                    _local.value = _local.value.copy(error = "Layaway is not active")
                CancelLayawayUseCase.Result.NotAuthorized ->
                    _local.value = _local.value.copy(error = "Only an admin can cancel a layaway")
                is CancelLayawayUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun revertCompletion(layawayId: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = revertCompletedLayawayUseCase(
                RevertCompletedLayawayUseCase.Params(layawayId, userId),
            )) {
                RevertCompletedLayawayUseCase.Result.Success ->
                    snackbarController.showSuccess("Layaway reverted to active")
                RevertCompletedLayawayUseCase.Result.RecordNotFound ->
                    _local.value = _local.value.copy(error = "Record not found")
                RevertCompletedLayawayUseCase.Result.NotCompleted ->
                    _local.value = _local.value.copy(error = "Layaway is not in COMPLETED state")
                is RevertCompletedLayawayUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun deletePayment(transactionId: String, layawayId: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = deletePaymentUseCase(
                DeleteLayawayPaymentUseCase.Params(transactionId, layawayId, userId),
            )) {
                DeleteLayawayPaymentUseCase.Result.Success -> {
                    snackbarController.showSuccess("Payment deleted")
                    /* observeForCustomer auto-refreshes */
                }
                is DeleteLayawayPaymentUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun clearSuccess() { _local.value = _local.value.copy(success = null) }
    fun clearError() { _local.value = _local.value.copy(error = null) }

    private data class LocalState(
        val customerName: String = "",
        val entries: List<LayawayEntryState> = emptyList(),
        val isLoading: Boolean = true,
        val success: String? = null,
        val error: String? = null,
    )
}
