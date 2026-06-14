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
        val name = params.name.trim()
        // Race-safe insert: regenerate the ID and retry on UNIQUE conflict.
        // The conflict can happen when two devices add a product with the
        // same name+rate+category combo at roughly the same time — both
        // read the same sequence count, then one of the inserts collides.
        var attempt = 0
        var product: Product
        while (true) {
            val now = System.currentTimeMillis()
            val id = productIdGenerator.generate(
                name = name,
                metalRateName = params.metalRateName,
                categoryName = params.categoryName,
            )
            product = Product(
                id = id,
                name = name,
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
            if (productRepository.tryAddNew(product)) break
            attempt++
            if (attempt >= MAX_ID_RETRIES) {
                error("Could not generate unique product ID for '$name' after $MAX_ID_RETRIES attempts")
            }
        }

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

    private companion object {
        const val MAX_ID_RETRIES = 5
    }
}
