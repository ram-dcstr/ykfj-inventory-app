package com.ykfj.inventory.domain.usecase.product

import androidx.paging.PagingData
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchProductsUseCase @Inject constructor(
    private val productRepository: ProductRepository,
) {
    operator fun invoke(query: String): Flow<PagingData<Product>> =
        productRepository.searchProductsPaged(query)
}
