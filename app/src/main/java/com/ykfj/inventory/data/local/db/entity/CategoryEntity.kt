package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_deleted"]),
    ],
)
data class CategoryEntity(
    @PrimaryKey val category_id: String,
    val name: String,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
