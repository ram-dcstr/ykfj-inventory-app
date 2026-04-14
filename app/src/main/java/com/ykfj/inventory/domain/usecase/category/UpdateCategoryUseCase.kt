package com.ykfj.inventory.domain.usecase.category

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.repository.CategoryRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

class UpdateCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data class Success(val category: Category) : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(
        id: String,
        name: String,
        actorUserId: String,
    ): Result {
        val existing = categoryRepository.getById(id) ?: return Result.NotFound
        val updated = existing.copy(
            name = name.trim(),
            updatedAt = System.currentTimeMillis(),
        )
        categoryRepository.upsert(updated)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.UPDATE,
            description = "Updated category '${updated.name}'",
            entityType = "category",
            entityId = updated.id,
            oldValue = existing.name,
            newValue = updated.name,
        )
        return Result.Success(updated)
    }
}
