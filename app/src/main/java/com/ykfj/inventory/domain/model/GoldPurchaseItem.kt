package com.ykfj.inventory.domain.model

/**
 * One line item within a [GoldPurchaseRecord].
 *
 * [buyRatePerGram] is always manually entered — never derived from [metalRateId].
 * [metalRateId] is stored only so the UI can show a label (e.g. "18K Saudi")
 * next to the manual rate as a reference point.
 *
 * [finalValue] = [overrideValue] ?: [computedValue]
 * [computedValue] = [weightGrams] × [buyRatePerGram]
 */
data class GoldPurchaseItem(
    val id: String,
    val purchaseRecordId: String,
    val description: String,
    val weightGrams: Double,
    val metalRateId: String?,
    val purity: String?,
    val buyRatePerGram: Double,
    val computedValue: Double,
    val overrideValue: Double?,
    val finalValue: Double,
    val photoFilename: String?,
    val soldToSupplierAt: Long? = null,
    val soldToSupplierPrice: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
) {
    val isSoldToSupplier: Boolean get() = soldToSupplierAt != null
    /** [soldToSupplierPrice] − [finalValue]; null while still in stock. */
    val profitFromSupplier: Double? get() = soldToSupplierPrice?.let { it - finalValue }
}
