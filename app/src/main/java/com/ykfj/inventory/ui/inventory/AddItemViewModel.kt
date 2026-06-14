package com.ykfj.inventory.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.model.ProductImage
import com.ykfj.inventory.domain.repository.ProductImageRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.category.GetCategoriesUseCase
import com.ykfj.inventory.domain.usecase.metalrate.GetMetalRatesUseCase
import com.ykfj.inventory.domain.usecase.product.AddProductUseCase
import com.ykfj.inventory.domain.usecase.product.UpdateProductUseCase
import com.ykfj.inventory.domain.usecase.supplier.GetSuppliersUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.ui.components.SnackbarController
import com.ykfj.inventory.util.CurrencyFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private fun slugify(value: String): String =
    value.uppercase().replace(Regex("[^A-Z0-9]"), "").take(12)

data class AddItemUiState(
    val isEditMode: Boolean = false,
    val existingProduct: Product? = null,
    val existingImage: ProductImage? = null,
    val categories: List<Category> = emptyList(),
    val metalRates: List<MetalRate> = emptyList(),
    val suppliers: List<com.ykfj.inventory.domain.model.Supplier> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val savedProductId: String? = null,
    val error: String? = null,
    /** Live selling price preview for weighted items. */
    val weightedPricePreview: String? = null,
    /** Auto-generated product ID preview. */
    val productIdPreview: String? = null,
)

@HiltViewModel
class AddItemViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getCategories: GetCategoriesUseCase,
    getMetalRates: GetMetalRatesUseCase,
    getSuppliers: GetSuppliersUseCase,
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val addProduct: AddProductUseCase,
    private val updateProduct: UpdateProductUseCase,
    private val sessionManager: SessionManager,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    /** Null = add mode, non-null = edit mode. */
    private val editProductId: String? = savedStateHandle["productId"]

    private val _formData = MutableStateFlow(FormData())
    private val _saveState = MutableStateFlow(SaveState())

    val uiState: StateFlow<AddItemUiState> = combine(
        getCategories(),
        getMetalRates(),
        getSuppliers(),
        _formData,
        _saveState,
    ) { categories, rates, suppliers, form, save ->
        val weightedPreview = computeWeightedPreview(form, rates)
        val idPreview = buildIdPreview(form, rates, categories)
        AddItemUiState(
            isEditMode = editProductId != null,
            existingProduct = save.existingProduct,
            existingImage = save.existingImage,
            categories = categories,
            metalRates = rates,
            suppliers = suppliers,
            isLoading = save.isLoading,
            isSaving = save.isSaving,
            savedProductId = save.savedProductId,
            error = save.error,
            weightedPricePreview = weightedPreview,
            productIdPreview = idPreview,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddItemUiState(),
    )

    init {
        if (editProductId != null) {
            viewModelScope.launch {
                val product = productRepository.getById(editProductId)
                val image = product?.let { productImageRepository.getForProduct(it.id) }
                _saveState.value = _saveState.value.copy(
                    existingProduct = product,
                    existingImage = image,
                    isLoading = false,
                )
            }
        } else {
            _saveState.value = _saveState.value.copy(isLoading = false)
        }
    }

    fun clearError() { _saveState.value = _saveState.value.copy(error = null) }

    fun submit(
        name: String,
        categoryId: String,
        categoryName: String,
        metalRateId: String?,
        metalRateName: String?,
        supplierId: String?,
        dateAcquired: Long,
        pricingType: PricingType,
        capitalPrice: Double,
        sellingPrice: Double?,
        weightGrams: Double?,
        size: String?,
        quantity: Int,
        notes: String?,
        imageFile: File?,
    ) {
        val name = name.trim()
        val userId = sessionManager.currentUser.value?.id ?: return
        if (name.isBlank()) { _saveState.value = _saveState.value.copy(error = "Name is required"); return }
        if (capitalPrice <= 0) { _saveState.value = _saveState.value.copy(error = "Capital price must be > 0"); return }
        if (pricingType == PricingType.FIXED && (sellingPrice == null || sellingPrice <= 0)) {
            _saveState.value = _saveState.value.copy(error = "Selling price must be > 0 for fixed items"); return
        }
        if (pricingType == PricingType.WEIGHTED && (weightGrams == null || weightGrams <= 0)) {
            _saveState.value = _saveState.value.copy(error = "Weight must be > 0 for weighted items"); return
        }
        if (quantity < 1) { _saveState.value = _saveState.value.copy(error = "Quantity must be at least 1"); return }

        _saveState.value = _saveState.value.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val productId = if (editProductId == null) {
                    addProduct(
                        AddProductUseCase.Params(
                            name = name,
                            categoryId = categoryId,
                            categoryName = categoryName,
                            metalRateId = metalRateId,
                            metalRateName = metalRateName,
                            supplierId = supplierId,
                            dateAcquired = dateAcquired,
                            pricingType = pricingType,
                            capitalPrice = capitalPrice,
                            sellingPrice = sellingPrice,
                            weightGrams = weightGrams,
                            size = size,
                            quantity = quantity,
                            notes = notes,
                            imageFile = imageFile,
                            actorUserId = userId,
                        ),
                    ).id
                } else {
                    val result = updateProduct(
                        UpdateProductUseCase.Params(
                            id = editProductId,
                            name = name,
                            categoryId = categoryId,
                            categoryName = categoryName,
                            metalRateId = metalRateId,
                            metalRateName = metalRateName,
                            supplierId = supplierId,
                            dateAcquired = dateAcquired,
                            pricingType = pricingType,
                            capitalPrice = capitalPrice,
                            sellingPrice = sellingPrice,
                            weightGrams = weightGrams,
                            size = size,
                            quantity = quantity,
                            notes = notes,
                            newImageFile = imageFile,
                            actorUserId = userId,
                        ),
                    )
                    when (result) {
                        is UpdateProductUseCase.Result.Success -> result.product.id
                        UpdateProductUseCase.Result.NotFound -> {
                            _saveState.value = _saveState.value.copy(isSaving = false, error = "Product not found")
                            return@launch
                        }
                    }
                }
                _saveState.value = _saveState.value.copy(isSaving = false, savedProductId = productId)
                snackbarController.showSuccess(
                    if (editProductId == null) "Product \"$name\" added" else "Product \"$name\" updated",
                )
            } catch (e: Exception) {
                _saveState.value = _saveState.value.copy(isSaving = false, error = e.message ?: "Save failed")
            }
        }
    }

    private fun computeWeightedPreview(form: FormData, rates: List<MetalRate>): String? {
        val rateId = form.selectedMetalRateId ?: return null
        val weight = form.weightGrams ?: return null
        val rate = rates.firstOrNull { it.id == rateId } ?: return null
        return CurrencyFormatter.format(weight * rate.pricePerGram)
    }

    private fun buildIdPreview(
        form: FormData,
        rates: List<MetalRate>,
        categories: List<Category>,
    ): String? {
        if (form.name.isBlank() || form.categoryId.isBlank()) return null
        val nameSlug = slugify(form.name)
        val rateSlug = if (form.selectedMetalRateId != null) {
            rates.firstOrNull { it.id == form.selectedMetalRateId }?.let { slugify(it.name) } ?: "?????"
        } else {
            "FIXED"
        }
        val catSlug = categories.firstOrNull { it.id == form.categoryId }?.let { slugify(it.name) }
            ?: return null
        return "$nameSlug-$rateSlug-$catSlug-??????"
    }

    fun onFormDataChanged(name: String, categoryId: String, metalRateId: String?, weight: Double?) {
        _formData.value = _formData.value.copy(
            name = name,
            categoryId = categoryId,
            selectedMetalRateId = metalRateId,
            weightGrams = weight,
        )
    }

    private data class FormData(
        val name: String = "",
        val categoryId: String = "",
        val selectedMetalRateId: String? = null,
        val weightGrams: Double? = null,
    )

    private data class SaveState(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val savedProductId: String? = null,
        val existingProduct: Product? = null,
        val existingImage: ProductImage? = null,
        val error: String? = null,
    )
}
