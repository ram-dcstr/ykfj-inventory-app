package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.data.local.image.ImageStorageManager
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.repository.ProductImageRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import com.ykfj.inventory.util.ProductIdGenerator
import java.io.File
import javax.inject.Inject

class UpdateProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val imageStorageManager: ImageStorageManager,
    private val logActivity: LogActivityUseCase,
    private val productIdGenerator: ProductIdGenerator,
) {
    sealed interface Result {
        data class Success(val product: Product) : Result
        data object NotFound : Result
    }

    data class Params(
        val id: String,
        val name: String,
        val categoryId: String,
        val categoryName: String,
        val metalRateId: String?,
        val metalRateName: String?,
        val supplierId: String?,
        val dateAcquired: Long,
        val pricingType: PricingType,
        val capitalPrice: Double,
        val sellingPrice: Double?,
        val weightGrams: Double?,
        val size: String?,
        /** Admin can only increase quantity; decrease is handled by sell/damage/layaway actions. */
        val quantity: Int,
        val notes: String?,
        /** New image file to replace the current one; null means no change. */
        val newImageFile: File?,
        val actorUserId: String,
    )

    suspend operator fun invoke(params: Params): Result {
        val existing = productRepository.getById(params.id) ?: return Result.NotFound

        // Regenerate product ID when name, metal rate, or category changes —
        // these three components are baked into the ID format NAME-RATE-CAT-XXXXXX.
        val idChanged = params.name.trim() != existing.name ||
            params.metalRateId != existing.metalRateId ||
            params.categoryId != existing.categoryId
        val newId = if (idChanged) {
            productIdGenerator.generate(
                name = params.name.trim(),
                metalRateName = params.metalRateName,
                categoryName = params.categoryName,
            )
        } else {
            existing.id
        }

        if (idChanged) productRepository.renameId(existing.id, newId)

        val updated = existing.copy(
            id = newId,
            name = params.name.trim(),
            categoryId = params.categoryId,
            metalRateId = params.metalRateId,
            supplierId = params.supplierId,
            dateAcquired = params.dateAcquired,
            pricingType = params.pricingType,
            capitalPrice = params.capitalPrice,
            sellingPrice = if (params.pricingType == PricingType.FIXED) params.sellingPrice else null,
            weightGrams = if (params.pricingType == PricingType.WEIGHTED) params.weightGrams else null,
            size = params.size?.trim()?.ifBlank { null },
            quantity = params.quantity.coerceAtLeast(existing.quantity),
            notes = params.notes?.trim()?.ifBlank { null },
            updatedAt = System.currentTimeMillis(),
        )
        productRepository.upsert(updated)

        if (params.newImageFile != null) {
            productImageRepository.getForProduct(params.id)
                ?.let { imageStorageManager.deleteImage(it) }
            imageStorageManager.saveImage(params.id, params.newImageFile)
        }

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.UPDATE,
            description = "Updated product '${updated.name}' (${updated.id})",
            entityType = "product",
            entityId = updated.id,
            oldValue = existing.name,
            newValue = updated.name,
        )
        return Result.Success(updated)
    }
}
