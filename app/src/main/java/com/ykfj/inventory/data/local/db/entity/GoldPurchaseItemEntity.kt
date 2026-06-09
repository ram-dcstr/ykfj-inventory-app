package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line item within a [GoldPurchaseRecordEntity].
 *
 * Buy rate is always manually entered — [buy_rate_per_gram] is never derived
 * from [metal_rate_id]. The rate-id is stored only so the UI can display
 * a label (e.g. "18K Saudi") next to the manual rate as a reference.
 *
 * [final_value] = [override_value] ?: [computed_value] where
 * [computed_value] = [weight_grams] × [buy_rate_per_gram].
 */
@Entity(
    tableName = "gold_purchase_items",
    indices = [
        Index(value = ["purchase_record_id"]),
        Index(value = ["metal_rate_id"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class GoldPurchaseItemEntity(
    @PrimaryKey val id: String,
    val purchase_record_id: String,
    val description: String,
    val weight_grams: Double,
    /** Optional label reference only — NOT used for price computation. */
    val metal_rate_id: String? = null,
    /** Free-text purity tag (e.g. "18K", "22K") shown alongside the manual buy rate. */
    val purity: String? = null,
    val buy_rate_per_gram: Double,
    val computed_value: Double,
    val override_value: Double? = null,
    val final_value: Double,
    val photo_filename: String? = null,
    /** Set when the shop sells this item on to its own supplier (null = still in stock). */
    val sold_to_supplier_at: Long? = null,
    /** Manually-entered price the supplier paid. Profit = this − [final_value]. */
    val sold_to_supplier_price: Double? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
