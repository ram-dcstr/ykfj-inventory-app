package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 virtual table mirroring [ProductEntity] for full-text search.
 * Linked via Room's `contentEntity` so the FTS index stays in sync
 * whenever a row in `products` changes.
 *
 * Queried with MATCH — e.g. `SELECT product_id FROM products_fts WHERE products_fts MATCH :query`.
 */
@Fts4(contentEntity = ProductEntity::class)
@Entity(tableName = "products_fts")
data class ProductFts(
    val name: String,
    val product_id: String,
    val notes: String?,
)
