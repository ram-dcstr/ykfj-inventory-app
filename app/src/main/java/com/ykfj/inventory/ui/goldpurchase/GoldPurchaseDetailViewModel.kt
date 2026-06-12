package com.ykfj.inventory.ui.goldpurchase

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.GoldPurchaseItem
import com.ykfj.inventory.domain.model.GoldPurchaseRecord
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.usecase.goldpurchase.GetGoldPurchaseDetailUseCase
import com.ykfj.inventory.domain.usecase.goldpurchase.MarkGoldSoldToSupplierUseCase
import com.ykfj.inventory.domain.usecase.goldpurchase.RevertGoldPurchaseUseCase
import com.ykfj.inventory.domain.usecase.goldpurchase.RevertTradeInUseCase
import com.ykfj.inventory.domain.usecase.goldpurchase.UnmarkGoldSoldToSupplierUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.ui.components.SnackbarController
import com.ykfj.inventory.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GoldPurchaseDetailUiState(
    val record: GoldPurchaseRecord? = null,
    val items: List<GoldPurchaseItem> = emptyList(),
    val customerName: String? = null,
    val canRevert: Boolean = false,
    val canRevertSold: Boolean = false,
    /** Admin/Manager gate for "Revert Trade-in" — undoes both the purchase and the linked sale atomically. */
    val canRevertTradeIn: Boolean = false,
    /** Admin gate for per-item "Mark as in stock" — undoing a sale is admin-only. */
    val canUnmarkItem: Boolean = false,
    val isReverting: Boolean = false,
    val revertError: String? = null,
    val isLoading: Boolean = true,
    val isReverted: Boolean = false,
) {
    val isTradeIn: Boolean get() = record?.linkedSoldRecordId != null
    val soldItemCount: Int get() = items.count { it.isSoldToSupplier }
}

@HiltViewModel
class GoldPurchaseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDetailUseCase: GetGoldPurchaseDetailUseCase,
    private val revertUseCase: RevertGoldPurchaseUseCase,
    private val revertTradeInUseCase: RevertTradeInUseCase,
    private val markSoldUseCase: MarkGoldSoldToSupplierUseCase,
    private val unmarkSoldUseCase: UnmarkGoldSoldToSupplierUseCase,
    private val customerRepository: CustomerRepository,
    private val sessionManager: SessionManager,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val purchaseId: String = checkNotNull(savedStateHandle["purchaseId"])

    private val _extras = MutableStateFlow(Extras())

    val uiState: StateFlow<GoldPurchaseDetailUiState> = combine(
        getDetailUseCase(purchaseId),
        _extras,
        sessionManager.currentUser,
    ) { (record, items), extras, user ->
        val role = user?.role
        val isAdmin = role == UserRole.ADMIN
        val isAdminOrManager = isAdmin || role == UserRole.MANAGER
        val hasSoldItems = items.any { it.isSoldToSupplier }
        val notDeleted = record != null && !record.isDeleted
        val isTradeIn = record?.linkedSoldRecordId != null
        // Mutually exclusive: only one record-level action is offered at a time.
        //  - canRevertSold: while items are sold to supplier, the admin must revert
        //    those first (deleting the parent would silently strip them from
        //    supplier-revenue analytics).
        //  - canRevertTradeIn: trade-in records can only be reverted via the atomic
        //    flow (RevertTradeInUseCase), since unwinding only one side would leave
        //    inventory and analytics inconsistent.
        //  - canRevert: plain purchases — admin-only soft delete.
        val canRevertSold = isAdmin && notDeleted && hasSoldItems
        val canRevertTradeIn = isAdminOrManager && notDeleted && isTradeIn && !hasSoldItems
        val canRevert = isAdmin && notDeleted && !isTradeIn && !hasSoldItems
        GoldPurchaseDetailUiState(
            record = record,
            items = items,
            customerName = extras.customerName,
            canRevert = canRevert,
            canRevertSold = canRevertSold,
            canRevertTradeIn = canRevertTradeIn,
            canUnmarkItem = isAdmin,
            isReverting = extras.isReverting,
            revertError = extras.revertError,
            isLoading = extras.isLoading,
            isReverted = extras.isReverted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GoldPurchaseDetailUiState(),
    )

    init {
        viewModelScope.launch {
            val record = getDetailUseCase(purchaseId).first().first
            val customerName = record?.customerId?.let { cid ->
                customerRepository.observeAll().first().firstOrNull { it.id == cid }?.name
            }
            _extras.value = _extras.value.copy(customerName = customerName, isLoading = false)
        }
    }

    fun revert(reason: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        if (_extras.value.isReverting) return
        _extras.value = _extras.value.copy(isReverting = true, revertError = null)
        viewModelScope.launch {
            when (val result = revertUseCase(
                RevertGoldPurchaseUseCase.Params(
                    recordId = purchaseId,
                    reason = reason,
                    actorUserId = userId,
                ),
            )) {
                RevertGoldPurchaseUseCase.Result.Success -> {
                    snackbarController.showSuccess("Gold purchase reverted")
                    _extras.value = _extras.value.copy(isReverting = false, isReverted = true)
                }
                RevertGoldPurchaseUseCase.Result.NotFound ->
                    _extras.value = _extras.value.copy(isReverting = false, revertError = "Purchase not found")
                RevertGoldPurchaseUseCase.Result.IsTradeIn ->
                    _extras.value = _extras.value.copy(isReverting = false, revertError = "This is a trade-in — use Revert Trade-in")
                is RevertGoldPurchaseUseCase.Result.Error ->
                    _extras.value = _extras.value.copy(isReverting = false, revertError = result.message)
            }
        }
    }

    /**
     * Admin/Manager only: atomically reverts a trade-in — both the gold purchase
     * record and its linked sold record are soft-deleted and the product quantity
     * is restored in one transaction. UI gating happens via [GoldPurchaseDetailUiState.canRevertTradeIn].
     */
    fun revertTradeIn(reason: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        if (_extras.value.isReverting) return
        _extras.value = _extras.value.copy(isReverting = true, revertError = null)
        viewModelScope.launch {
            when (val result = revertTradeInUseCase(
                RevertTradeInUseCase.Params(
                    goldPurchaseRecordId = purchaseId,
                    reason = reason,
                    actorUserId = userId,
                ),
            )) {
                RevertTradeInUseCase.Result.Success -> {
                    snackbarController.showSuccess("Trade-in reverted · sale and stock restored")
                    _extras.value = _extras.value.copy(isReverting = false, isReverted = true)
                }
                RevertTradeInUseCase.Result.NotFound ->
                    _extras.value = _extras.value.copy(isReverting = false, revertError = "Trade-in record or its linked sale could not be found")
                is RevertTradeInUseCase.Result.Error ->
                    _extras.value = _extras.value.copy(isReverting = false, revertError = result.message)
            }
        }
    }

    /** Returns every sold item in this record back to in-stock. Admin-only at the UI layer. */
    fun revertAllSold() {
        val userId = sessionManager.currentUser.value?.id ?: return
        if (_extras.value.isReverting) return
        val soldItemIds = uiState.value.items
            .filter { it.isSoldToSupplier }
            .map { it.id }
        if (soldItemIds.isEmpty()) return
        _extras.value = _extras.value.copy(isReverting = true, revertError = null)
        viewModelScope.launch {
            var failures = 0
            soldItemIds.forEach { id ->
                val result = unmarkSoldUseCase(UnmarkGoldSoldToSupplierUseCase.Params(id, userId))
                if (result !is UnmarkGoldSoldToSupplierUseCase.Result.Success) failures++
            }
            _extras.value = _extras.value.copy(
                isReverting = false,
                revertError = if (failures > 0) "Failed to revert $failures item(s)" else null,
            )
            val reverted = soldItemIds.size - failures
            if (reverted > 0) {
                snackbarController.showSuccess("$reverted item${if (reverted == 1) "" else "s"} returned to stock")
            }
        }
    }

    fun clearRevertError() {
        _extras.value = _extras.value.copy(revertError = null)
    }

    fun markItemSoldToSupplier(itemId: String, supplierPrice: Double) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val result = markSoldUseCase(
                MarkGoldSoldToSupplierUseCase.Params(itemId, supplierPrice, userId),
            )) {
                MarkGoldSoldToSupplierUseCase.Result.Success ->
                    snackbarController.showSuccess("Marked sold to supplier · ${CurrencyFormatter.format(supplierPrice)}")
                MarkGoldSoldToSupplierUseCase.Result.InvalidPrice ->
                    _extras.value = _extras.value.copy(revertError = "Supplier price must be greater than 0")
                is MarkGoldSoldToSupplierUseCase.Result.Error ->
                    _extras.value = _extras.value.copy(revertError = result.message)
            }
        }
    }

    fun unmarkItemSoldToSupplier(itemId: String) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            when (val result = unmarkSoldUseCase(
                UnmarkGoldSoldToSupplierUseCase.Params(itemId, userId),
            )) {
                UnmarkGoldSoldToSupplierUseCase.Result.Success ->
                    snackbarController.showSuccess("Item returned to stock")
                is UnmarkGoldSoldToSupplierUseCase.Result.Error ->
                    _extras.value = _extras.value.copy(revertError = result.message)
            }
        }
    }

    private data class Extras(
        val customerName: String? = null,
        val isLoading: Boolean = true,
        val isReverting: Boolean = false,
        val revertError: String? = null,
        val isReverted: Boolean = false,
    )
}
