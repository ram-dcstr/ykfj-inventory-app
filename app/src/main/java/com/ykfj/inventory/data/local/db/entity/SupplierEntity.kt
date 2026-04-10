package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "suppliers",
    indices = [
        Index(value = ["name"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class SupplierEntity(
    @PrimaryKey val supplier_id: String,
    val name: String,
    val representative_name: String? = null,
    val mobile: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
