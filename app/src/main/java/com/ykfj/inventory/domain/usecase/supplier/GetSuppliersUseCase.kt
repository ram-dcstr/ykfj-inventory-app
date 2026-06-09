package com.ykfj.inventory.domain.usecase.supplier

import com.ykfj.inventory.domain.model.Supplier
import com.ykfj.inventory.domain.repository.SupplierRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSuppliersUseCase @Inject constructor(
    private val supplierRepository: SupplierRepository,
) {
    operator fun invoke(): Flow<List<Supplier>> = supplierRepository.observeAll()
}
