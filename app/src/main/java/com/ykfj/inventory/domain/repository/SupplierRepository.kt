package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.Supplier
import kotlinx.coroutines.flow.Flow

interface SupplierRepository {

    fun observeAll(): Flow<List<Supplier>>

    suspend fun getById(id: String): Supplier?

    suspend fun upsert(supplier: Supplier)

    suspend fun delete(id: String)
}
