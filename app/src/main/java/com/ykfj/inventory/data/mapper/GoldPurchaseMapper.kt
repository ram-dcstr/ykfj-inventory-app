package com.ykfj.inventory.data.mapper

import com.ykfj.inventory.data.local.db.entity.GoldPurchaseItemEntity
import com.ykfj.inventory.data.local.db.entity.GoldPurchaseRecordEntity
import com.ykfj.inventory.domain.model.GoldPurchaseItem
import com.ykfj.inventory.domain.model.GoldPurchaseRecord

internal fun GoldPurchaseRecordEntity.toDomain(): GoldPurchaseRecord = GoldPurchaseRecord(
    id = id,
    customerId = customer_id,
    totalPaid = total_paid,
    paidAt = paid_at,
    notes = notes,
    recordedBy = recorded_by,
    linkedSoldRecordId = linked_sold_record_id,
    createdAt = created_at,
    updatedAt = updated_at,
    isDeleted = is_deleted,
)

internal fun GoldPurchaseRecord.toEntity(): GoldPurchaseRecordEntity = GoldPurchaseRecordEntity(
    id = id,
    customer_id = customerId,
    total_paid = totalPaid,
    paid_at = paidAt,
    notes = notes,
    recorded_by = recordedBy,
    linked_sold_record_id = linkedSoldRecordId,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = isDeleted,
)

internal fun GoldPurchaseItemEntity.toDomain(): GoldPurchaseItem = GoldPurchaseItem(
    id = id,
    purchaseRecordId = purchase_record_id,
    description = description,
    weightGrams = weight_grams,
    metalRateId = metal_rate_id,
    purity = purity,
    buyRatePerGram = buy_rate_per_gram,
    computedValue = computed_value,
    overrideValue = override_value,
    finalValue = final_value,
    photoFilename = photo_filename,
    soldToSupplierAt = sold_to_supplier_at,
    soldToSupplierPrice = sold_to_supplier_price,
    createdAt = created_at,
    updatedAt = updated_at,
    isDeleted = is_deleted,
)

internal fun GoldPurchaseItem.toEntity(): GoldPurchaseItemEntity = GoldPurchaseItemEntity(
    id = id,
    purchase_record_id = purchaseRecordId,
    description = description,
    weight_grams = weightGrams,
    metal_rate_id = metalRateId,
    purity = purity,
    buy_rate_per_gram = buyRatePerGram,
    computed_value = computedValue,
    override_value = overrideValue,
    final_value = finalValue,
    photo_filename = photoFilename,
    sold_to_supplier_at = soldToSupplierAt,
    sold_to_supplier_price = soldToSupplierPrice,
    created_at = createdAt,
    updated_at = updatedAt,
    is_deleted = isDeleted,
)
