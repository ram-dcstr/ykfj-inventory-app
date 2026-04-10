package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class CustomerEntity(
    @PrimaryKey val customer_id: String,
    val name: String,
    val mobile: String? = null,
    val phone: String? = null,
    val birthday: Long? = null,
    val address: String? = null,
    /** Credit score starts at 100. -3 layaway late, -2 paluwagan late, +1 on-time. */
    val credit_score: Int = 100,
    val notes: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
