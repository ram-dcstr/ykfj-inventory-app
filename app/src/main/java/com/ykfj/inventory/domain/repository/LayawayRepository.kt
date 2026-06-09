package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.model.LayawayTransaction
import kotlinx.coroutines.flow.Flow

/**
 * Owns both [LayawayRecord] and its child [LayawayTransaction] rows — they
 * are always mutated together (adding a payment must also update
 * `total_paid` on the parent), so a single repository keeps that invariant
 * in one place.
 */
interface LayawayRepository {

    fun observeActive(): Flow<List<LayawayRecord>>

    fun observeCompleted(): Flow<List<LayawayRecord>>

    fun observeForCustomer(customerId: String): Flow<List<LayawayRecord>>

    fun observeTransactions(layawayId: String): Flow<List<LayawayTransaction>>

    suspend fun getById(id: String): LayawayRecord?

    /** Returns the single ACTIVE layaway for a product, or null if none. */
    suspend fun getActiveForProduct(productId: String): LayawayRecord?

    suspend fun insert(record: LayawayRecord)

    /** Admin-only: edit any field except `product_id`. */
    suspend fun update(record: LayawayRecord)

    /** Inserts a transaction and recomputes parent `total_paid`. */
    suspend fun addPayment(transaction: LayawayTransaction)

    /** Deletes a transaction and recomputes parent `total_paid`. */
    suspend fun deletePayment(transactionId: String)

    /** Marks COMPLETED and stamps `completion_date`. */
    suspend fun markCompleted(id: String, completionDate: Long)

    /** Inverse of [markCompleted] — flips status back to ACTIVE and clears completion_date. Admin-only revert. */
    suspend fun revertCompletion(id: String)

    /**
     * Marks CANCELLED, stamps `completion_date`, and sets
     * `forfeited_amount = total_paid`. No refunds.
     */
    suspend fun markCancelled(id: String, completionDate: Long)

    suspend fun softDelete(id: String)

    suspend fun archive(id: String)
}
