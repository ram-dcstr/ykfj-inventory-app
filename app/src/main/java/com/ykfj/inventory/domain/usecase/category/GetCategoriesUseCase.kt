package com.ykfj.inventory.domain.usecase.category

import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(): Flow<List<Category>> = categoryRepository.observeAll()
}
