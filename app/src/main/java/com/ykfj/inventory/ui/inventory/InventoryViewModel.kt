package com.ykfj.inventory.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.model.MetalRate
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.repository.ProductImageRepository
import com.ykfj.inventory.domain.usecase.category.GetCategoriesUseCase
import com.ykfj.inventory.domain.usecase.metalrate.GetMetalRatesUseCase
import com.ykfj.inventory.domain.usecase.product.GetProductsUseCase
import com.ykfj.inventory.domain.usecase.product.SearchProductsUseCase
import com.ykfj.inventory.ui.auth.SessionManager
import com.ykfj.inventory.util.PermissionChecker
import com.ykfj.inventory.util.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val getProducts: GetProductsUseCase,
    private val searchProducts: SearchProductsUseCase,
    getMetalRates: GetMetalRatesUseCase,
    getCategories: GetCategoriesUseCase,
    productImageRepository: ProductImageRepository,
    sessionManager: SessionManager,
) : ViewModel() {

    val permissions: StateFlow<Permissions> = sessionManager.currentUser
        .map { PermissionChecker.forRole(it?.role) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Permissions.NONE)

    private val _searchQuery = MutableStateFlow("")

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Current metal rates — used by the UI to compute WEIGHTED selling prices on list cards. */
    val metalRates: StateFlow<List<MetalRate>> = getMetalRates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** rateId → rate, so [sellingPriceFor] is an O(1) lookup per card instead of a list scan. */
    private val metalRateById: StateFlow<Map<String, MetalRate>> = metalRates
        .map { rates -> rates.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val categories: StateFlow<List<Category>> = getCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** productId → thumbFileName for all products that have an image. */
    val thumbMap: StateFlow<Map<String, String>> = productImageRepository.observeThumbMap()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Paged product stream. Reacts to search query changes. */
    val products: Flow<PagingData<Product>> =
        _searchQuery
            .debounce(300)
            .flatMapLatest { query ->
                if (query.isBlank()) getProducts() else searchProducts(query)
            }
            .cachedIn(viewModelScope)

    fun onSearchQuery(query: String) { _searchQuery.value = query }

    /** Computes the display selling price for a product using current metal rates. */
    fun sellingPriceFor(product: Product): Double? = when (product.pricingType) {
        PricingType.WEIGHTED -> {
            val rate = product.metalRateId?.let { metalRateById.value[it] }
            product.weightGrams?.let { w -> rate?.let { r -> w * r.pricePerGram } }
        }
        PricingType.FIXED -> product.sellingPrice
    }
}
