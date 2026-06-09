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
import com.ykfj.inventory.domain.usecase.layaway.SplitLayawayPaymentUseCase
import com.ykfj.inventory.domain.usecase.layaway.UpdateLayawayUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LayawayDetailUiState(
    val record: LayawayRecord? = null,
    val productName: String = "",
    val customerName: String = "",
    val transactions: List<LayawayTransaction> = emptyList(),
    val otherActiveLayaways: List<LayawayRow> = emptyList(),
    val canEdit: Boolean = false,
    val canAddPayment: Boolean = false,
    val isLoading: Boolean = true,
    val success: String? = null,
    val error: String? = null,
)

@HiltViewModel
class LayawayDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val layawayRepository: LayawayRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val addPaymentUseCase: AddLayawayPaymentUseCase,
    private val splitPaymentUseCase: SplitLayawayPaymentUseCase,
    private val updateLayawayUseCase: UpdateLayawayUseCase,
    private val completeLayawayUseCase: CompleteLayawayUseCase,
    private val cancelLayawayUseCase: CancelLayawayUseCase,
    private val deletePaymentUseCase: DeleteLayawayPaymentUseCase,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val layawayId: String = checkNotNull(savedStateHandle["layawayId"])
    private val _local = MutableStateFlow(LocalState())

    val uiState: StateFlow<LayawayDetailUiState> = combine(
        layawayRepository.observeTransactions(layawayId),
        _local,
        sessionManager.currentUser,
    ) { transactions, local, user ->
        val isAdmin = user?.role == UserRole.ADMIN
        val isLoggedIn = user != null
        LayawayDetailUiState(
            record = local.record,
            productName = local.productName,
            customerName = local.customerName,
            transactions = transactions,
            otherActiveLayaways = local.otherActiveLayaways,
            // Edit (record fields, payment deletion): Admin only.
            canEdit = isAdmin && local.record?.status == LayawayStatus.ACTIVE,
            // Add payment: all roles per business rules.
            canAddPayment = isLoggedIn && local.record?.status == LayawayStatus.ACTIVE,
            isLoading = local.isLoading,
            success = local.success,
            error = local.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LayawayDetailUiState(),
    )

    init {
        loadRecord()
    }

    private fun loadRecord() {
        viewModelScope.launch {
            val record = layawayRepository.getById(layawayId)
            if (record == null) {
                _local.value = _local.value.copy(isLoading = false, error = "Record not found")
                return@launch
            }
            val product = productRepository.getById(record.productId)
            val customer = customerRepository.getById(record.customerId)
            _local.value = _local.value.copy(
                record = record,
                productName = product?.name ?: record.productId,
                customerName = customer?.name ?: record.customerId,
                isLoading = false,
            )
            loadOtherLayaways(record)
        }
    }

    private fun loadOtherLayaways(current: LayawayRecord) {
        viewModelScope.launch {
            val all = layawayRepository.observeForCustomer(current.customerId).first()
            val productMap = all.map { it.productId }.distinct()
                .associateWith { productRepository.getById(it) }
            val others = all
                .filter { it.status == LayawayStatus.ACTIVE && it.id != layawayId }
                .map { r ->
                    LayawayRow(
                        id = r.id,
                        productId = r.productId,
                        productName = productMap[r.productId]?.name ?: r.productId,
                        customerId = r.customerId,
                        customerName = _local.value.customerName,
                        quantity = r.quantity,
                        unitPrice = r.unitPrice,
                        totalPaid = r.totalPaid,
                        dueDate = r.dueDate,
                        createdAt = r.createdAt,
                        status = r.status,
                        completionDate = r.completionDate,
                    )
                }
            _local.value = _local.value.copy(otherActiveLayaways = others)
        }
    }

    fun addPayment(amount: Double, paymentMethod: PaymentMethod = PaymentMethod.CASH, notes: String?) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = addPaymentUseCase(AddLayawayPaymentUseCase.Params(layawayId, amount, notes, userId, paymentMethod = paymentMethod))) {
                AddLayawayPaymentUseCase.Result.Success -> {
                    _local.value = _local.value.copy(success = "Payment added")
                    refreshRecord()
                }
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
        val record = _local.value.record ?: return
        viewModelScope.launch {
            when (val r = splitPaymentUseCase(
                SplitLayawayPaymentUseCase.Params(allocations, record.customerId, userId, paymentMethod = paymentMethod),
            )) {
                SplitLayawayPaymentUseCase.Result.Success -> {
                    _local.value = _local.value.copy(success = "Split payment applied")
                    refreshRecord()
                    loadOtherLayaways(record)
                }
                is SplitLayawayPaymentUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun updateLayaway(customerId: String, quantity: Int, unitPrice: Double, dueDate: Long?) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = updateLayawayUseCase(
                UpdateLayawayUseCase.Params(layawayId, customerId, quantity, unitPrice, dueDate, userId),
            )) {
                UpdateLayawayUseCase.Result.Success -> {
                    _local.value = _local.value.copy(success = "Layaway updated")
                    refreshRecord()
                }
                UpdateLayawayUseCase.Result.RecordNotFound ->
                    _local.value = _local.value.copy(error = "Record not found")
                UpdateLayawayUseCase.Result.NotActive ->
                    _local.value = _local.value.copy(error = "Layaway is not active")
                UpdateLayawayUseCase.Result.InsufficientQuantity ->
                    _local.value = _local.value.copy(error = "Not enough product quantity available")
                is UpdateLayawayUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun completeLayaway() {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = completeLayawayUseCase(CompleteLayawayUseCase.Params(layawayId, userId))) {
                CompleteLayawayUseCase.Result.Success -> {
                    _local.value = _local.value.copy(success = "Layaway marked as completed")
                    refreshRecord()
                }
                CompleteLayawayUseCase.Result.RecordNotFound ->
                    _local.value = _local.value.copy(error = "Record not found")
                CompleteLayawayUseCase.Result.NotActive ->
                    _local.value = _local.value.copy(error = "Layaway is not active")
                is CompleteLayawayUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun cancelLayaway() {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = cancelLayawayUseCase(CancelLayawayUseCase.Params(layawayId, userId))) {
                CancelLayawayUseCase.Result.Success -> {
                    _local.value = _local.value.copy(success = "Layaway cancelled")
                    refreshRecord()
                }
                CancelLayawayUseCase.Result.RecordNotFound ->
                    _local.value = _local.value.copy(error = "Record not found")
                CancelLayawayUseCase.Result.NotActive ->
                    _local.value = _local.value.copy(error = "Layaway is not active")
                is CancelLayawayUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun deletePayment(transactionId: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val r = deletePaymentUseCase(
                DeleteLayawayPaymentUseCase.Params(transactionId, layawayId, userId),
            )) {
                DeleteLayawayPaymentUseCase.Result.Success -> { /* Flow auto-refreshes transactions */ }
                is DeleteLayawayPaymentUseCase.Result.Error ->
                    _local.value = _local.value.copy(error = r.message)
            }
        }
    }

    fun clearSuccess() {
        _local.value = _local.value.copy(success = null)
    }

    fun clearError() {
        _local.value = _local.value.copy(error = null)
    }

    private fun refreshRecord() {
        viewModelScope.launch {
            val record = layawayRepository.getById(layawayId) ?: return@launch
            _local.value = _local.value.copy(record = record)
        }
    }

    private data class LocalState(
        val record: LayawayRecord? = null,
        val productName: String = "",
        val customerName: String = "",
        val otherActiveLayaways: List<LayawayRow> = emptyList(),
        val isLoading: Boolean = true,
        val success: String? = null,
        val error: String? = null,
    )
}
