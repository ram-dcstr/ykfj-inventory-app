package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.PricingType
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.data.local.image.ImageStorageManager
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import com.ykfj.inventory.util.ProductIdGenerator
import java.io.File
import javax.inject.Inject

class AddProductUseCase @Inject constructor(
    private val productRepository: ProductRepository,
    private val imageStorageManager: ImageStorageManager,
    private val productIdGenerator: ProductIdGenerator,
    private val logActivity: LogActivityUseCase,
) {
    data class Params(
        val name: String,
        val categoryId: String,
        val categoryName: String,
        val metalRateId: String?,
        val metalRateName: String?,
        val supplierId: String?,
        val dateAcquired: Long,
        val pricingType: PricingType,
        val capitalPrice: Double,
        /** Required for FIXED items, null for WEIGHTED. */
        val sellingPrice: Double?,
        /** Required for WEIGHTED items, null for FIXED. */
        val weightGrams: Double?,
        val size: String?,
        val quantity: Int,
        val notes: String?,
        /** Raw image file from camera or gallery — null if no photo. */
        val imageFile: File?,
        val actorUserId: String,
    )

    suspend operator fun invoke(params: Params): Product {
        val now = System.currentTimeMillis()
        val id = productIdGenerator.generate(
            name = params.name,
            metalRateName = params.metalRateName,
            categoryName = params.categoryName,
            metalRateId = params.metalRateId,
            categoryId = params.categoryId,
        )
        val product = Product(
            id = id,
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
            quantity = params.quantity,
            notes = params.notes?.trim()?.ifBlank { null },
            status = ProductStatus.AVAILABLE,
            createdAt = now,
            updatedAt = now,
        )
        productRepository.upsert(product)

        params.imageFile?.let { imageStorageManager.saveImage(product.id, it) }

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.CREATE,
            description = "Added product '${product.name}' (${product.id})",
            entityType = "product",
            entityId = product.id,
        )
        return product
    }
}
