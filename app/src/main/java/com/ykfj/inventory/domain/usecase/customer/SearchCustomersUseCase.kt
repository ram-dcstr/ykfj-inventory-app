package com.ykfj.inventory.domain.usecase.customer

import com.ykfj.inventory.domain.model.Customer
import com.ykfj.inventory.domain.repository.CustomerRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchCustomersUseCase @Inject constructor(
    private val customerRepository: CustomerRepository,
) {
    operator fun invoke(query: String): Flow<List<Customer>> =
        customerRepository.search(query)
}
