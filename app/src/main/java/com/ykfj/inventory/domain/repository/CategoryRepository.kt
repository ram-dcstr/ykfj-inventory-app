package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.Category
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {

    fun observeAll(): Flow<List<Category>>

    suspend fun getById(id: String): Category?

    suspend fun upsert(category: Category)

    /**
     * Soft delete. Callers must first ensure no active products reference
     * this category — see the deletion guard in `Inventory-Rules.md`.
     */
    suspend fun delete(id: String)

    suspend fun countActiveProducts(categoryId: String): Int
}
