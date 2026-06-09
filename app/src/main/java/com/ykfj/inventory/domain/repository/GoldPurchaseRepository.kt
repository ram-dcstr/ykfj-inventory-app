package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.GoldPurchaseItem
import com.ykfj.inventory.domain.model.GoldPurchaseRecord
import kotlinx.coroutines.flow.Flow

interface GoldPurchaseRepository {

    fun observeAll(): Flow<List<GoldPurchaseRecord>>

    fun observeAllItems(): Flow<List<GoldPurchaseItem>>

    fun observeById(id: String): Flow<GoldPurchaseRecord?>

    suspend fun getById(id: String): GoldPurchaseRecord?

    fun observeItemsForRecord(recordId: String): Flow<List<GoldPurchaseItem>>

    suspend fun getItemsForRecord(recordId: String): List<GoldPurchaseItem>

    suspend fun getItemCountForRecord(recordId: String): Int

    /** Inserts the header record and all item lines in a single transaction. */
    suspend fun insert(record: GoldPurchaseRecord, items: List<GoldPurchaseItem>)

    suspend fun softDelete(recordId: String, updatedAt: Long)

    suspend fun softDeleteItems(recordId: String, updatedAt: Long)

    suspend fun markItemSoldToSupplier(itemId: String, price: Double, soldAt: Long, updatedAt: Long)

    suspend fun unmarkItemSoldToSupplier(itemId: String, updatedAt: Long)

    /** Sum of supplier profit (sold_to_supplier_price − final_value) for items sold within the window. */
    fun observeSupplierProfit(start: Long, end: Long): Flow<Double>

    /** Sum of supplier revenue (sold_to_supplier_price) for items sold within the window. */
    fun observeSupplierRevenue(start: Long, end: Long): Flow<Double>

    /** Count of items sold to supplier within the window. */
    fun observeSupplierSoldCount(start: Long, end: Long): Flow<Int>
}
