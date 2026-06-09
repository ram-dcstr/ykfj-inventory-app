package com.ykfj.inventory.data.remote.sync

import kotlinx.serialization.Serializable

// ── Auth ─────────────────────────────────────────────────────────────────────

@Serializable data class LoginRequest(val username: String, val password: String)

@Serializable data class LoginResponse(val token: String, val user: UserSyncDto)

@Serializable data class SyncStatusResponse(val server_time: Long, val device_id: String)

@Serializable data class PushResponse(val accepted: Boolean, val server_time: Long)

// ── Users ─────────────────────────────────────────────────────────────────────
//
// The bcrypt hash is included so a user created on the phone can fully sync
// (login working on either device after a push/pull cycle). Both devices are
// owner-controlled and equally trusted; the hash itself is non-reversible.

@Serializable
data class UserSyncDto(
    val user_id: String,
    val username: String,
    val password_hash: String,
    val name: String,
    val role: String,
    val is_active: Boolean,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Products ──────────────────────────────────────────────────────────────────

@Serializable
data class ProductSyncDto(
    val product_id: String,
    val name: String,
    val category_id: String,
    val metal_rate_id: String? = null,
    val supplier_id: String? = null,
    val date_acquired: Long,
    val pricing_type: String,
    val capital_price: Double,
    val selling_price: Double? = null,
    val weight_grams: Double? = null,
    val size: String? = null,
    val quantity: Int,
    val notes: String? = null,
    val status: String,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Customers ─────────────────────────────────────────────────────────────────

@Serializable
data class CustomerSyncDto(
    val customer_id: String,
    val name: String,
    val mobile: String? = null,
    val phone: String? = null,
    val birthday: Long? = null,
    val address: String? = null,
    val credit_score: Int,
    val notes: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Metal Rates ───────────────────────────────────────────────────────────────

@Serializable
data class MetalRateSyncDto(
    val rate_id: String,
    val name: String,
    val price_per_gram: Double,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Categories ────────────────────────────────────────────────────────────────

@Serializable
data class CategorySyncDto(
    val category_id: String,
    val name: String,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Suppliers ─────────────────────────────────────────────────────────────────

@Serializable
data class SupplierSyncDto(
    val supplier_id: String,
    val name: String,
    val representative_name: String? = null,
    val mobile: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Sold Records ──────────────────────────────────────────────────────────────

@Serializable
data class SoldRecordSyncDto(
    val sold_id: String,
    val product_id: String,
    val customer_id: String? = null,
    val sold_by: String,
    val quantity: Int,
    val sold_price: Double,
    val capital_price: Double,
    val discount_amount: Double,
    val discount_type: String,
    val sold_date: Long,
    val notes: String? = null,
    val payment_method: String = "CASH",
    val is_archived: Boolean,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Layaway Records ───────────────────────────────────────────────────────────

@Serializable
data class LayawayRecordSyncDto(
    val layaway_id: String,
    val product_id: String,
    val customer_id: String,
    val created_by: String,
    val quantity: Int,
    val unit_price: Double,
    val total_paid: Double,
    val due_date: Long? = null,
    val status: String,
    val completion_date: Long? = null,
    val forfeited_amount: Double? = null,
    val is_archived: Boolean,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Layaway Transactions ──────────────────────────────────────────────────────

@Serializable
data class LayawayTransactionSyncDto(
    val transaction_id: String,
    val layaway_id: String,
    val amount_paid: Double,
    val payment_date: Long,
    val notes: String? = null,
    val payment_method: String = "CASH",
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Damaged Records ───────────────────────────────────────────────────────────

@Serializable
data class DamagedRecordSyncDto(
    val damaged_id: String,
    val product_id: String,
    val recorded_by: String,
    val reason: String,
    val date_recorded: Long,
    val notes: String? = null,
    val is_archived: Boolean,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Paluwagan Groups ──────────────────────────────────────────────────────────

@Serializable
data class PaluwaganGroupSyncDto(
    val group_id: String,
    val name: String,
    val contribution_amount: Double,
    val frequency_days: Int,
    val total_slots: Int,
    val current_round: Int,
    val status: String,
    val start_date: Long,
    val notes: String? = null,
    val is_archived: Boolean,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Paluwagan Slots ───────────────────────────────────────────────────────────

@Serializable
data class PaluwaganSlotSyncDto(
    val slot_id: String,
    val group_id: String,
    val customer_id: String,
    val original_customer_id: String? = null,
    val position: Int,
    val pot_collected_at: Long? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Paluwagan Payments ────────────────────────────────────────────────────────

@Serializable
data class PaluwaganPaymentSyncDto(
    val payment_id: String,
    val group_id: String,
    val slot_id: String,
    val round_number: Int,
    val amount_paid: Double,
    val payment_date: Long? = null,
    val status: String,
    val notes: String? = null,
    val payment_channel: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Product Images ────────────────────────────────────────────────────────────

@Serializable
data class ProductImageSyncDto(
    val image_id: String,
    val product_id: String,
    val file_name: String,
    val file_size_bytes: Long,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Activity Logs ─────────────────────────────────────────────────────────────

@Serializable
data class ActivityLogSyncDto(
    val log_id: String,
    val user_id: String,
    val action: String,
    val entity_type: String? = null,
    val entity_id: String? = null,
    val description: String,
    val old_value: String? = null,
    val new_value: String? = null,
    val timestamp: Long,
)

// ── Gold Purchase Records ─────────────────────────────────────────────────────

@Serializable
data class GoldPurchaseRecordSyncDto(
    val id: String,
    val customer_id: String? = null,
    val total_paid: Double,
    val paid_at: Long,
    val notes: String? = null,
    val recorded_by: String,
    val linked_sold_record_id: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Gold Purchase Items ───────────────────────────────────────────────────────

@Serializable
data class GoldPurchaseItemSyncDto(
    val id: String,
    val purchase_record_id: String,
    val description: String,
    val weight_grams: Double,
    val metal_rate_id: String? = null,
    val purity: String? = null,
    val buy_rate_per_gram: Double,
    val computed_value: Double,
    val override_value: Double? = null,
    val final_value: Double,
    val photo_filename: String? = null,
    val sold_to_supplier_at: Long? = null,
    val sold_to_supplier_price: Double? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Cash Movements ────────────────────────────────────────────────────────────

@Serializable
data class CashMovementSyncDto(
    val id: String,
    val type: String,
    val amount: Double,
    val date: Long,
    val notes: String? = null,
    val recorded_by: String,
    val recorded_at: Long,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean,
)

// ── Sync Wrapper ──────────────────────────────────────────────────────────────

@Serializable
data class ChangesPayload(
    val products: List<ProductSyncDto> = emptyList(),
    val customers: List<CustomerSyncDto> = emptyList(),
    val sold_records: List<SoldRecordSyncDto> = emptyList(),
    val layaway_records: List<LayawayRecordSyncDto> = emptyList(),
    val layaway_transactions: List<LayawayTransactionSyncDto> = emptyList(),
    val damaged_records: List<DamagedRecordSyncDto> = emptyList(),
    val metal_rates: List<MetalRateSyncDto> = emptyList(),
    val categories: List<CategorySyncDto> = emptyList(),
    val suppliers: List<SupplierSyncDto> = emptyList(),
    val users: List<UserSyncDto> = emptyList(),
    val product_images: List<ProductImageSyncDto> = emptyList(),
    val paluwagan_groups: List<PaluwaganGroupSyncDto> = emptyList(),
    val paluwagan_slots: List<PaluwaganSlotSyncDto> = emptyList(),
    val paluwagan_payments: List<PaluwaganPaymentSyncDto> = emptyList(),
    val activity_logs: List<ActivityLogSyncDto> = emptyList(),
    val gold_purchase_records: List<GoldPurchaseRecordSyncDto> = emptyList(),
    val gold_purchase_items: List<GoldPurchaseItemSyncDto> = emptyList(),
    val cash_movements: List<CashMovementSyncDto> = emptyList(),
    val server_time: Long,
)
