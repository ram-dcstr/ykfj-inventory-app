package com.ykfj.inventory.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.model.DamagedRecord
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.model.ProductImage
import com.ykfj.inventory.domain.model.Supplier
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.ProductImageRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.category.GetCategoriesUseCase
import com.ykfj.inventory.domain.usecase.goldpurchase.AddGoldPurchaseUseCase
import com.ykfj.inventory.domain.usecase.goldpurchase.SellWithTradeInUseCase
import com.ykfj.inventory.domain.usecase.metalrate.GetMetalRatesUseCase
import com.ykfj.inventory.domain.usecase.product.MarkAsDamagedUseCase
import com.ykfj.inventory.domain.usecase.layaway.AddLayawayPaymentUseCase
import com.ykfj.inventory.domain.usecase.product.MarkAsLayawayUseCase
import com.ykfj.inventory.domain.usecase.product.MarkAsSoldUseCase
import com.ykfj.inventory.domain.usecase.product.RevertStatusUseCase
import com.ykfj.inventory.domain.usecase.supplier.GetSuppliersUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.ui.components.SnackbarController
import com.ykfj.inventory.ui.goldpurchase.GoldPurchaseItemDraft
import com.ykfj.inventory.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StatusDialog { NONE, SELL, LAYAWAY, DAMAGED, REVERT }

data class ProductDetailUiState(
    val product: Product? = null,
    val image: ProductImage? = null,
    val categoryName: String = "",
    val metalRateName: String? = null,
    val supplierName: String? = null,
    /** Selling price: computed for WEIGHTED (weight × rate), stored for FIXED. */
    val sellingPrice: Double? = null,
    /** sellingPrice − capitalPrice — Admin only. */
    val profitAmount: Double? = null,
    /** (profit / sellingPrice) × 100 — Admin only. */
    val profitMarginPct: Double? = null,
    val isAdmin: Boolean = false,
    val isAdminOrManager: Boolean = false,
    val canEdit: Boolean = false,
    val canRevert: Boolean = false,
    val activeDialog: StatusDialog = StatusDialog.NONE,
    val actionError: String? = null,
    /** Active (non-reverted) damaged records for this product, newest first. */
    val damagedRecords: List<DamagedRecord> = emptyList(),
    /** userId → display name for resolving recordedBy on damaged records. */
    val userNamesById: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    productRepository: ProductRepository,
    productImageRepository: ProductImageRepository,
    getCategories: GetCategoriesUseCase,
    getMetalRates: GetMetalRatesUseCase,
    getSuppliers: GetSuppliersUseCase,
    private val customerRepository: CustomerRepository,
    private val damagedRecordRepository: DamagedRecordRepository,
    private val userRepository: UserRepository,
    private val markAsSold: MarkAsSoldUseCase,
    private val sellWithTradeIn: SellWithTradeInUseCase,
    private val markAsLayaway: MarkAsLayawayUseCase,
    private val addLayawayPayment: AddLayawayPaymentUseCase,
    private val markAsDamaged: MarkAsDamagedUseCase,
    private val revertStatus: RevertStatusUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val productId: String = checkNotNull(savedStateHandle["productId"])

    private val _dialogState = MutableStateFlow(StatusDialog.NONE)
    private val _actionError = MutableStateFlow<String?>(null)
    private val _pickedCustomer = MutableStateFlow<Customer?>(null)
    val pickedCustomer: StateFlow<Customer?> = _pickedCustomer.asStateFlow()

    val uiState: StateFlow<ProductDetailUiState> = combine(
        productRepository.observeById(productId),
        productImageRepository.observeForProduct(productId),
        getCategories(),
        getMetalRates(),
        getSuppliers(),
        _dialogState,
        _actionError,
        damagedRecordRepository.observeForProduct(productId),
        userRepository.observeActiveUsers(),
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        buildState(
            product = args[0] as Product?,
            image = args[1] as ProductImage?,
            categories = args[2] as List<Category>,
            rates = args[3] as List<MetalRate>,
            suppliers = args[4] as List<Supplier>,
            dialog = args[5] as StatusDialog,
            actionError = args[6] as String?,
            damagedRecords = args[7] as List<DamagedRecord>,
            users = args[8] as List<User>,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProductDetailUiState(),
    )

    private fun buildState(
        product: Product?,
        image: ProductImage?,
        categories: List<Category>,
        rates: List<MetalRate>,
        suppliers: List<Supplier>,
        dialog: StatusDialog,
        actionError: String?,
        damagedRecords: List<DamagedRecord>,
        users: List<User>,
    ): ProductDetailUiState {
        if (product == null) return ProductDetailUiState(isLoading = false, error = "Product not found")

        val role = sessionManager.currentUser.value?.role
        val isAdmin = role == UserRole.ADMIN
        val isAdminOrManager = isAdmin || role == UserRole.MANAGER

        val metalRate = rates.firstOrNull { it.id == product.metalRateId }
        val sellingPrice = when (product.pricingType) {
            PricingType.WEIGHTED -> metalRate?.let { r -> product.weightGrams?.let { w -> w * r.pricePerGram } }
            PricingType.FIXED -> product.sellingPrice
        }
        val profitAmount = if (isAdmin) sellingPrice?.let { it - product.capitalPrice } else null
        val profitMarginPct = if (isAdmin) sellingPrice?.let { sp ->
            if (sp > 0) ((sp - product.capitalPrice) / sp) * 100 else null
        } else null

        return ProductDetailUiState(
            product = product,
            image = image,
            categoryName = categories.firstOrNull { it.id == product.categoryId }?.name ?: product.categoryId,
            metalRateName = metalRate?.let { "${it.name} (${CurrencyFormatter.format(it.pricePerGram)}/g)" },
            supplierName = suppliers.firstOrNull { it.id == product.supplierId }?.name,
            sellingPrice = sellingPrice,
            profitAmount = profitAmount,
            profitMarginPct = profitMarginPct,
            isAdmin = isAdmin,
            isAdminOrManager = isAdminOrManager,
            canEdit = isAdmin,
            canRevert = isAdminOrManager && (product.status == ProductStatus.SOLD || product.status == ProductStatus.DAMAGED),
            activeDialog = dialog,
            actionError = actionError,
            damagedRecords = damagedRecords,
            userNamesById = users.associate { it.id to it.name },
            isLoading = false,
        )
    }

    fun openDialog(dialog: StatusDialog) { _dialogState.value = dialog }
    fun dismissDialog() { _dialogState.value = StatusDialog.NONE }
    fun dismissActionError() { _actionError.value = null }

    fun setPickedCustomer(customerId: String) {
        viewModelScope.launch {
            _pickedCustomer.value = customerRepository.getById(customerId)
            _dialogState.value = StatusDialog.LAYAWAY
        }
    }

    fun consumePickedCustomer() { _pickedCustomer.value = null }

    fun submitSell(
        quantity: Int,
        soldPrice: Double,
        customerId: String?,
        discountAmount: Double,
        discountType: DiscountType,
        paymentMethod: PaymentMethod = PaymentMethod.CASH,
        notes: String?,
        tradeInItems: List<GoldPurchaseItemDraft> = emptyList(),
    ) {
        val userId = sessionManager.currentUser.value?.id ?: return
        val product = uiState.value.product ?: return
        viewModelScope.launch {
            if (tradeInItems.isEmpty()) {
                // Plain sale path — no trade-in.
                val result = markAsSold(
                    MarkAsSoldUseCase.Params(
                        productId = productId,
                        actorUserId = userId,
                        quantity = quantity,
                        soldPrice = soldPrice,
                        capitalPrice = product.capitalPrice,
                        customerId = customerId,
                        discountAmount = discountAmount,
                        discountType = discountType,
                        paymentMethod = paymentMethod,
                        notes = notes,
                    ),
                )
                when (result) {
                    is MarkAsSoldUseCase.Result.Success -> {
                        snackbarController.showSuccess(
                            "Sale of ${CurrencyFormatter.format(soldPrice * quantity)} recorded",
                        )
                        dismissDialog()
                    }
                    MarkAsSoldUseCase.Result.ProductNotFound -> _actionError.value = "Product not found"
                    MarkAsSoldUseCase.Result.InsufficientQuantity -> _actionError.value = "Not enough units available"
                }
            } else {
                // Trade-in path — sale + gold purchase atomically.
                val drafts = tradeInItems.map { d ->
                    AddGoldPurchaseUseCase.ItemDraft(
                        description = d.description,
                        weightGrams = d.weightValue ?: 0.0,
                        purity = d.purity,
                        buyRatePerGram = d.rateValue ?: 0.0,
                        overrideValue = if (d.overrideEnabled) d.overrideValue.toDoubleOrNull() else null,
                        photoFilename = null,
                    )
                }
                val result = sellWithTradeIn(
                    SellWithTradeInUseCase.Params(
                        productId = productId,
                        actorUserId = userId,
                        quantity = quantity,
                        soldPrice = soldPrice,
                        capitalPrice = product.capitalPrice,
                        customerId = customerId,
                        discountAmount = discountAmount,
                        discountType = discountType,
                        paymentMethod = paymentMethod,
                        saleNotes = notes,
                        tradeInItems = drafts,
                        tradeInNotes = null,
                    ),
                )
                when (result) {
                    is SellWithTradeInUseCase.Result.Success -> {
                        val saleTotal = soldPrice * quantity
                        val tradeInTotal = tradeInItems.sumOf {
                            it.finalValue ?: 0.0
                        }
                        val net = saleTotal - tradeInTotal
                        val summary = when {
                            net > 0 -> "Trade-in sale recorded · customer paid ${CurrencyFormatter.format(net)}"
                            net < 0 -> "Trade-in sale recorded · shop paid out ${CurrencyFormatter.format(-net)}"
                            else -> "Trade-in sale recorded · even swap"
                        }
                        snackbarController.showSuccess(summary)
                        dismissDialog()
                    }
                    SellWithTradeInUseCase.Result.ProductNotFound -> _actionError.value = "Product not found"
                    SellWithTradeInUseCase.Result.InsufficientQuantity -> _actionError.value = "Not enough units available"
                    SellWithTradeInUseCase.Result.NoItems -> _actionError.value = "Add at least one trade-in item"
                    SellWithTradeInUseCase.Result.InvalidItem -> _actionError.value = "Each trade-in item needs weight > 0 and rate > 0"
                }
            }
        }
    }

    fun submitLayaway(customerId: String, quantity: Int, unitPrice: Double, dueDate: Long?, downpayment: Double?) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            val result = markAsLayaway(
                MarkAsLayawayUseCase.Params(
                    productId = productId,
                    actorUserId = userId,
                    customerId = customerId,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    dueDate = dueDate,
                ),
            )
            when (result) {
                is MarkAsLayawayUseCase.Result.Success -> {
                    if (downpayment != null && downpayment > 0) {
                        addLayawayPayment(
                            AddLayawayPaymentUseCase.Params(
                                layawayId = result.layawayId,
                                amount = downpayment,
                                notes = "Downpayment",
                                actorUserId = userId,
                            ),
                        )
                    }
                    val totalDue = unitPrice * quantity
                    val msg = if (downpayment != null && downpayment > 0) {
                        "Layaway created · downpayment ${CurrencyFormatter.format(downpayment)} of ${CurrencyFormatter.format(totalDue)}"
                    } else {
                        "Layaway created · total ${CurrencyFormatter.format(totalDue)}"
                    }
                    snackbarController.showSuccess(msg)
                    dismissDialog()
                }
                MarkAsLayawayUseCase.Result.ProductNotFound -> _actionError.value = "Product not found"
                MarkAsLayawayUseCase.Result.InsufficientQuantity -> _actionError.value = "Not enough units available"
            }
        }
    }

    fun submitDamaged(reason: String, notes: String?) {
        val userId = sessionManager.currentUser.value?.id ?: return
        viewModelScope.launch {
            val result = markAsDamaged(
                MarkAsDamagedUseCase.Params(
                    productId = productId,
                    actorUserId = userId,
                    reason = reason,
                    notes = notes,
                ),
            )
            when (result) {
                is MarkAsDamagedUseCase.Result.Success -> {
                    snackbarController.showSuccess("Marked as damaged · 1 unit moved to Damaged screen")
                    dismissDialog()
                }
                MarkAsDamagedUseCase.Result.ProductNotFound -> _actionError.value = "Product not found"
                MarkAsDamagedUseCase.Result.NoUnitsAvailable -> _actionError.value = "No units available"
            }
        }
    }

    fun submitRevert(reason: String) {
        viewModelScope.launch {
            val result = revertStatus(
                RevertStatusUseCase.Params(productId = productId, reason = reason),
            )
            when (result) {
                RevertStatusUseCase.Result.Success -> {
                    snackbarController.showSuccess("Reverted · product restored to inventory")
                    dismissDialog()
                }
                RevertStatusUseCase.Result.ProductNotFound -> _actionError.value = "Product not found"
                RevertStatusUseCase.Result.NoRecordToRevert -> _actionError.value = "No record to revert"
                is RevertStatusUseCase.Result.Error -> _actionError.value = result.message
            }
        }
    }
}
