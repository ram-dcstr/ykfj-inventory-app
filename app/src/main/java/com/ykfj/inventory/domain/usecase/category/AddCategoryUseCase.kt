package com.ykfj.inventory.domain.usecase.category

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.Category
import com.ykfj.inventory.domain.repository.CategoryRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

class AddCategoryUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val logActivity: LogActivityUseCase,
) {
    suspend operator fun invoke(
        name: String,
        actorUserId: String,
    ): Category {
        val now = System.currentTimeMillis()
        val category = Category(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            createdAt = now,
            updatedAt = now,
        )
        categoryRepository.upsert(category)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.CREATE,
            description = "Added category '${category.name}'",
            entityType = "category",
            entityId = category.id,
        )
        return category
    }
}
