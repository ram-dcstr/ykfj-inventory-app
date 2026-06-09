package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.CashMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CashMovementDao {

    @Query(
        """
        SELECT * FROM cash_movements
        WHERE is_deleted = 0 AND date = :dayStartMillis
        ORDER BY recorded_at ASC
        """,
    )
    fun observeForDate(dayStartMillis: Long): Flow<List<CashMovementEntity>>

    @Query(
        """
        SELECT * FROM cash_movements
        WHERE is_deleted = 0
          AND date BETWEEN :startMillis AND :endMillis
        ORDER BY date ASC, recorded_at ASC
        """,
    )
    fun observeForDateRange(startMillis: Long, endMillis: Long): Flow<List<CashMovementEntity>>

    @Query(
        """
        SELECT * FROM cash_movements
        WHERE is_deleted = 0 AND date = :dayStartMillis
        ORDER BY recorded_at ASC
        """,
    )
    suspend fun getForDate(dayStartMillis: Long): List<CashMovementEntity>

    @Query("SELECT * FROM cash_movements WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CashMovementEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(movement: CashMovementEntity)

    @Update
    suspend fun update(movement: CashMovementEntity)

    @Query("UPDATE cash_movements SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM cash_movements WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<CashMovementEntity>
}
