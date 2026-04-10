package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query("SELECT * FROM customers WHERE is_deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<CustomerEntity>>

    @Query(
        """
        SELECT * FROM customers
        WHERE is_deleted = 0 AND name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE ASC
        LIMIT :limit
        """,
    )
    fun search(query: String, limit: Int = 20): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE customer_id = :customerId AND is_deleted = 0 LIMIT 1")
    suspend fun getById(customerId: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(customer: CustomerEntity)

    @Update
    suspend fun update(customer: CustomerEntity)

    @Query("UPDATE customers SET is_deleted = 1, updated_at = :now WHERE customer_id = :customerId")
    suspend fun softDelete(customerId: String, now: Long)

    @Query(
        """
        UPDATE customers
        SET credit_score = credit_score + :delta, updated_at = :now
        WHERE customer_id = :customerId
        """,
    )
    suspend fun adjustCreditScore(customerId: String, delta: Int, now: Long)

    @Query("SELECT * FROM customers WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<CustomerEntity>
}
