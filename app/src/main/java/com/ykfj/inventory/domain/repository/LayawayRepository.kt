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

    /**
     * Count of ACTIVE layaways whose `due_date` is strictly before [now] (overdue).
     * Powers the red badge on the Layaway sidebar entry. Caller must pass a
     * refreshed `now` periodically so newly-elapsed dates roll into the count.
     */
    fun observeOverdueCount(now: Long): Flow<Int>

    fun observeCompleted(): Flow<List<LayawayRecord>>

    fun observeForCustomer(customerId: String): Flow<List<LayawayRecord>>

    fun observeTransactions(layawayId: String): Flow<List<LayawayTransaction>>

    /** One-shot batch read of transactions for many layaways — avoids a Flow per record. */
    suspend fun getTransactionsForLayaways(layawayIds: List<String>): List<LayawayTransaction>

    suspend fun getById(id: String): LayawayRecord?

    /** Returns the single ACTIVE layaway for a product, or null if none. */
    suspend fun getActiveForProduct(productId: String): LayawayRecord?

    /** Count of non-deleted ACTIVE layawnays referencing [productId]. Used by the delete-product guard. */
    suspend fun countActiveForProduct(productId: String): Int

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
