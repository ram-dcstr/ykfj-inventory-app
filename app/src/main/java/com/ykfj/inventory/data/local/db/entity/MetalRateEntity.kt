package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metal_rates",
    indices = [
        Index(value = ["name"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class MetalRateEntity(
    @PrimaryKey val rate_id: String,
    val name: String,
    val price_per_gram: Double,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
