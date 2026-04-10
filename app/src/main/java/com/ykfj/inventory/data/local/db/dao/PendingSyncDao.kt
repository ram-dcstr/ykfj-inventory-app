package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.PendingSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSyncDao {

    @Query("SELECT * FROM pending_sync_queue ORDER BY created_at ASC")
    fun observeAll(): Flow<List<PendingSyncEntity>>

    @Query("SELECT * FROM pending_sync_queue WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPending(): List<PendingSyncEntity>

    @Query("SELECT COUNT(*) FROM pending_sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: PendingSyncEntity)

    @Update
    suspend fun update(entry: PendingSyncEntity)

    @Query(
        """
        UPDATE pending_sync_queue
        SET status = :status, updated_at = :now WHERE id = :id
        """,
    )
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query("DELETE FROM pending_sync_queue WHERE status = 'SYNCED'")
    suspend fun clearSynced()

    @Query("DELETE FROM pending_sync_queue WHERE id = :id")
    suspend fun delete(id: String)
}
