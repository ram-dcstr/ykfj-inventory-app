package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SupplierDao {

    @Query("SELECT * FROM suppliers WHERE is_deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE supplier_id = :supplierId AND is_deleted = 0 LIMIT 1")
    suspend fun getById(supplierId: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(supplier: SupplierEntity)

    @Update
    suspend fun update(supplier: SupplierEntity)

    @Query("UPDATE suppliers SET is_deleted = 1, updated_at = :now WHERE supplier_id = :supplierId")
    suspend fun softDelete(supplierId: String, now: Long)

    @Query("SELECT * FROM suppliers WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<SupplierEntity>
}
