package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.MetalRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MetalRateDao {

    @Query("SELECT * FROM metal_rates WHERE is_deleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<MetalRateEntity>>

    @Query("SELECT * FROM metal_rates WHERE rate_id = :rateId AND is_deleted = 0 LIMIT 1")
    suspend fun getById(rateId: String): MetalRateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rate: MetalRateEntity)

    @Update
    suspend fun update(rate: MetalRateEntity)

    @Query("UPDATE metal_rates SET is_deleted = 1, updated_at = :now WHERE rate_id = :rateId")
    suspend fun softDelete(rateId: String, now: Long)

    @Query("SELECT * FROM metal_rates WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<MetalRateEntity>
}
