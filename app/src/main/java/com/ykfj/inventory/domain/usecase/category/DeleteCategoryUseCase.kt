package com.ykfj.inventory.domain.usecase.category

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.repository.CategoryRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

class DeleteCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data object Success : Result
        data object NotFound : Result
        data class Blocked(val activeProductCount: Int) : Result
    }

    suspend operator fun invoke(id: String, actorUserId: String): Result {
        val existing = categoryRepository.getById(id) ?: return Result.NotFound

        val activeProducts = categoryRepository.countActiveProducts(id)
        if (activeProducts > 0) return Result.Blocked(activeProducts)

        categoryRepository.delete(id)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.DELETE,
            description = "Deleted category '${existing.name}'",
            entityType = "category",
            entityId = id,
        )
        return Result.Success
    }
}
