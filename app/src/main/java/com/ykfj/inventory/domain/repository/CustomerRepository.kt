package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.Customer
import kotlinx.coroutines.flow.Flow

interface CustomerRepository {

    fun observeAll(): Flow<List<Customer>>

    /** Name/mobile prefix search — backs the customer auto-suggest composable. */
    fun search(query: String): Flow<List<Customer>>

    suspend fun getById(id: String): Customer?

    /** Batch lookup — one query for many ids, used to enrich lists without N+1. */
    suspend fun getByIds(ids: List<String>): List<Customer>

    suspend fun upsert(customer: Customer)

    /** Credit score adjustments (+1 on-time, −3 layaway late, −2 paluwagan late). */
    suspend fun adjustCreditScore(id: String, delta: Int)

    suspend fun delete(id: String)
}
