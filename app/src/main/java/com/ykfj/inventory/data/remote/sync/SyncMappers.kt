package com.ykfj.inventory.data.remote.sync

import com.ykfj.inventory.data.local.db.entity.*
import com.ykfj.inventory.data.local.db.enums.*

// ── Entity → DTO ──────────────────────────────────────────────────────────────

fun UserEntity.toSyncDto() = UserSyncDto(
    user_id = user_id,
    username = username,
    password_hash = password_hash,
    name = name,
    role = role.name,
    is_active = is_active,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun UserSyncDto.toEntity() = UserEntity(
    user_id = user_id,
    username = username,
    password_hash = password_hash,
    name = name,
    role = runCatching { UserRole.valueOf(role) }.getOrDefault(UserRole.STAFF),
    is_active = is_active,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun ProductEntity.toSyncDto() = ProductSyncDto(
    product_id = product_id,
    name = name,
    category_id = category_id,
    metal_rate_id = metal_rate_id,
    supplier_id = supplier_id,
    date_acquired = date_acquired,
    pricing_type = pricing_type.name,
    capital_price = capital_price,
    selling_price = selling_price,
    weight_grams = weight_grams,
    size = size,
    quantity = quantity,
    notes = notes,
    status = status.name,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun CustomerEntity.toSyncDto() = CustomerSyncDto(
    customer_id = customer_id,
    name = name,
    mobile = mobile,
    phone = phone,
    birthday = birthday,
    address = address,
    credit_score = credit_score,
    notes = notes,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun MetalRateEntity.toSyncDto() = MetalRateSyncDto(
    rate_id = rate_id,
    name = name,
    price_per_gram = price_per_gram,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun CategoryEntity.toSyncDto() = CategorySyncDto(
    category_id = category_id,
    name = name,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun SupplierEntity.toSyncDto() = SupplierSyncDto(
    supplier_id = supplier_id,
    name = name,
    representative_name = representative_name,
    mobile = mobile,
    address = address,
    notes = notes,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun SoldRecordEntity.toSyncDto() = SoldRecordSyncDto(
    sold_id = sold_id,
    product_id = product_id,
    customer_id = customer_id,
    sold_by = sold_by,
    quantity = quantity,
    sold_price = sold_price,
    capital_price = capital_price,
    discount_amount = discount_amount,
    discount_type = discount_type.name,
    sold_date = sold_date,
    notes = notes,
    payment_method = payment_method.name,
    linked_layaway_id = linked_layaway_id,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun LayawayRecordEntity.toSyncDto() = LayawayRecordSyncDto(
    layaway_id = layaway_id,
    product_id = product_id,
    customer_id = customer_id,
    created_by = created_by,
    quantity = quantity,
    unit_price = unit_price,
    total_paid = total_paid,
    due_date = due_date,
    status = status.name,
    completion_date = completion_date,
    forfeited_amount = forfeited_amount,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun LayawayTransactionEntity.toSyncDto() = LayawayTransactionSyncDto(
    transaction_id = transaction_id,
    layaway_id = layaway_id,
    amount_paid = amount_paid,
    payment_date = payment_date,
    notes = notes,
    payment_method = payment_method.name,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun DamagedRecordEntity.toSyncDto() = DamagedRecordSyncDto(
    damaged_id = damaged_id,
    product_id = product_id,
    recorded_by = recorded_by,
    reason = reason,
    date_recorded = date_recorded,
    notes = notes,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun StockAdjustmentEntity.toSyncDto() = StockAdjustmentSyncDto(
    adjustment_id = adjustment_id,
    product_id = product_id,
    quantity = quantity,
    reason = reason,
    notes = notes,
    recorded_by = recorded_by,
    date_recorded = date_recorded,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun PaluwaganGroupEntity.toSyncDto() = PaluwaganGroupSyncDto(
    group_id = group_id,
    name = name,
    contribution_amount = contribution_amount,
    frequency_days = frequency_days,
    total_slots = total_slots,
    current_round = current_round,
    status = status.name,
    start_date = start_date,
    notes = notes,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun PaluwaganSlotEntity.toSyncDto() = PaluwaganSlotSyncDto(
    slot_id = slot_id,
    group_id = group_id,
    customer_id = customer_id,
    original_customer_id = original_customer_id,
    position = position,
    pot_collected_at = pot_collected_at,
    pot_payout_channel = pot_payout_channel,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun PaluwaganPaymentEntity.toSyncDto() = PaluwaganPaymentSyncDto(
    payment_id = payment_id,
    group_id = group_id,
    slot_id = slot_id,
    round_number = round_number,
    amount_paid = amount_paid,
    payment_date = payment_date,
    status = status.name,
    notes = notes,
    payment_channel = payment_channel,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun ProductImageEntity.toSyncDto() = ProductImageSyncDto(
    image_id = image_id,
    product_id = product_id,
    file_name = file_name,
    file_size_bytes = file_size_bytes,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun ProductImageSyncDto.toEntity() = ProductImageEntity(
    image_id = image_id,
    product_id = product_id,
    file_name = file_name,
    file_size_bytes = file_size_bytes,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun ActivityLogEntity.toSyncDto() = ActivityLogSyncDto(
    log_id = log_id,
    user_id = user_id,
    action = action.name,
    entity_type = entity_type,
    entity_id = entity_id,
    description = description,
    old_value = old_value,
    new_value = new_value,
    timestamp = timestamp,
)

// ── DTO → Entity ──────────────────────────────────────────────────────────────
// Used by the push sync endpoint to upsert phone-side changes into the tablet DB.

fun ProductSyncDto.toEntity() = ProductEntity(
    product_id = product_id,
    name = name,
    category_id = category_id,
    metal_rate_id = metal_rate_id,
    supplier_id = supplier_id,
    date_acquired = date_acquired,
    pricing_type = runCatching { PricingType.valueOf(pricing_type) }.getOrDefault(PricingType.FIXED),
    capital_price = capital_price,
    selling_price = selling_price,
    weight_grams = weight_grams,
    size = size,
    quantity = quantity,
    notes = notes,
    status = runCatching { ProductStatus.valueOf(status) }.getOrDefault(ProductStatus.AVAILABLE),
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun CustomerSyncDto.toEntity() = CustomerEntity(
    customer_id = customer_id,
    name = name,
    mobile = mobile,
    phone = phone,
    birthday = birthday,
    address = address,
    credit_score = credit_score,
    notes = notes,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun MetalRateSyncDto.toEntity() = MetalRateEntity(
    rate_id = rate_id,
    name = name,
    price_per_gram = price_per_gram,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun CategorySyncDto.toEntity() = CategoryEntity(
    category_id = category_id,
    name = name,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun SupplierSyncDto.toEntity() = SupplierEntity(
    supplier_id = supplier_id,
    name = name,
    representative_name = representative_name,
    mobile = mobile,
    address = address,
    notes = notes,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun SoldRecordSyncDto.toEntity() = SoldRecordEntity(
    sold_id = sold_id,
    product_id = product_id,
    customer_id = customer_id,
    sold_by = sold_by,
    quantity = quantity,
    sold_price = sold_price,
    capital_price = capital_price,
    discount_amount = discount_amount,
    discount_type = runCatching { DiscountType.valueOf(discount_type) }.getOrDefault(DiscountType.NONE),
    sold_date = sold_date,
    notes = notes,
    payment_method = runCatching { PaymentMethod.valueOf(payment_method) }.getOrDefault(PaymentMethod.CASH),
    linked_layaway_id = linked_layaway_id,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun LayawayRecordSyncDto.toEntity() = LayawayRecordEntity(
    layaway_id = layaway_id,
    product_id = product_id,
    customer_id = customer_id,
    created_by = created_by,
    quantity = quantity,
    unit_price = unit_price,
    total_paid = total_paid,
    due_date = due_date,
    status = runCatching { LayawayStatus.valueOf(status) }.getOrDefault(LayawayStatus.ACTIVE),
    completion_date = completion_date,
    forfeited_amount = forfeited_amount,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun LayawayTransactionSyncDto.toEntity() = LayawayTransactionEntity(
    transaction_id = transaction_id,
    layaway_id = layaway_id,
    amount_paid = amount_paid,
    payment_date = payment_date,
    notes = notes,
    payment_method = runCatching { PaymentMethod.valueOf(payment_method) }.getOrDefault(PaymentMethod.CASH),
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun DamagedRecordSyncDto.toEntity() = DamagedRecordEntity(
    damaged_id = damaged_id,
    product_id = product_id,
    recorded_by = recorded_by,
    reason = reason,
    date_recorded = date_recorded,
    notes = notes,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun StockAdjustmentSyncDto.toEntity() = StockAdjustmentEntity(
    adjustment_id = adjustment_id,
    product_id = product_id,
    quantity = quantity,
    reason = reason,
    notes = notes,
    recorded_by = recorded_by,
    date_recorded = date_recorded,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun PaluwaganGroupSyncDto.toEntity() = PaluwaganGroupEntity(
    group_id = group_id,
    name = name,
    contribution_amount = contribution_amount,
    frequency_days = frequency_days,
    total_slots = total_slots,
    current_round = current_round,
    status = runCatching { PaluwaganGroupStatus.valueOf(status) }.getOrDefault(PaluwaganGroupStatus.ACTIVE),
    start_date = start_date,
    notes = notes,
    is_archived = is_archived,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun PaluwaganSlotSyncDto.toEntity() = PaluwaganSlotEntity(
    slot_id = slot_id,
    group_id = group_id,
    customer_id = customer_id,
    original_customer_id = original_customer_id,
    position = position,
    pot_collected_at = pot_collected_at,
    pot_payout_channel = pot_payout_channel,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun PaluwaganPaymentSyncDto.toEntity() = PaluwaganPaymentEntity(
    payment_id = payment_id,
    group_id = group_id,
    slot_id = slot_id,
    round_number = round_number,
    amount_paid = amount_paid,
    payment_date = payment_date,
    status = runCatching { PaluwaganPaymentStatus.valueOf(status) }.getOrDefault(PaluwaganPaymentStatus.UNPAID),
    notes = notes,
    payment_channel = payment_channel,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

// ── Gold Purchase Records ─────────────────────────────────────────────────────

fun GoldPurchaseRecordEntity.toSyncDto() = GoldPurchaseRecordSyncDto(
    id = id,
    customer_id = customer_id,
    total_paid = total_paid,
    paid_at = paid_at,
    notes = notes,
    recorded_by = recorded_by,
    linked_sold_record_id = linked_sold_record_id,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun GoldPurchaseRecordSyncDto.toEntity() = GoldPurchaseRecordEntity(
    id = id,
    customer_id = customer_id,
    total_paid = total_paid,
    paid_at = paid_at,
    notes = notes,
    recorded_by = recorded_by,
    linked_sold_record_id = linked_sold_record_id,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

// ── Gold Purchase Items ───────────────────────────────────────────────────────

fun GoldPurchaseItemEntity.toSyncDto() = GoldPurchaseItemSyncDto(
    id = id,
    purchase_record_id = purchase_record_id,
    description = description,
    weight_grams = weight_grams,
    metal_rate_id = metal_rate_id,
    purity = purity,
    buy_rate_per_gram = buy_rate_per_gram,
    computed_value = computed_value,
    override_value = override_value,
    final_value = final_value,
    photo_filename = photo_filename,
    sold_to_supplier_at = sold_to_supplier_at,
    sold_to_supplier_price = sold_to_supplier_price,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun GoldPurchaseItemSyncDto.toEntity() = GoldPurchaseItemEntity(
    id = id,
    purchase_record_id = purchase_record_id,
    description = description,
    weight_grams = weight_grams,
    metal_rate_id = metal_rate_id,
    purity = purity,
    buy_rate_per_gram = buy_rate_per_gram,
    computed_value = computed_value,
    override_value = override_value,
    final_value = final_value,
    photo_filename = photo_filename,
    sold_to_supplier_at = sold_to_supplier_at,
    sold_to_supplier_price = sold_to_supplier_price,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

// ── Cash Movements ────────────────────────────────────────────────────────────

fun CashMovementEntity.toSyncDto() = CashMovementSyncDto(
    id = id,
    type = type.name,
    amount = amount,
    date = date,
    notes = notes,
    recorded_by = recorded_by,
    recorded_at = recorded_at,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

fun CashMovementSyncDto.toEntity() = CashMovementEntity(
    id = id,
    type = runCatching { CashMovementType.valueOf(type) }.getOrDefault(CashMovementType.ADJUSTMENT),
    amount = amount,
    date = date,
    notes = notes,
    recorded_by = recorded_by,
    recorded_at = recorded_at,
    created_at = created_at,
    updated_at = updated_at,
    is_deleted = is_deleted,
)

// ── Activity Logs (inbound — outbound mapper exists above) ────────────────────

fun ActivityLogSyncDto.toEntity() = ActivityLogEntity(
    log_id = log_id,
    user_id = user_id,
    action = runCatching { ActivityAction.valueOf(action) }.getOrDefault(ActivityAction.UPDATE),
    entity_type = entity_type,
    entity_id = entity_id,
    description = description,
    old_value = old_value,
    new_value = new_value,
    timestamp = timestamp,
)
