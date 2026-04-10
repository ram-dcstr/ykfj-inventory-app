package com.ykfj.inventory.data.local.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ykfj.inventory.data.local.db.entity.ActivityLogEntity
import com.ykfj.inventory.data.local.db.enums.ActivityAction

@Dao
interface ActivityLogDao {

    @Query("SELECT * FROM activity_logs ORDER BY timestamp DESC")
    fun pagingAll(): PagingSource<Int, ActivityLogEntity>

    @Query("SELECT * FROM activity_logs WHERE user_id = :userId ORDER BY timestamp DESC")
    fun pagingForUser(userId: String): PagingSource<Int, ActivityLogEntity>

    @Query(
        """
        SELECT * FROM activity_logs
        WHERE (:userId IS NULL OR user_id = :userId)
          AND (:action IS NULL OR action = :action)
          AND timestamp BETWEEN :startMillis AND :endMillis
        ORDER BY timestamp DESC
        """,
    )
    fun pagingFiltered(
        userId: String?,
        action: ActivityAction?,
        startMillis: Long,
        endMillis: Long,
    ): PagingSource<Int, ActivityLogEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: ActivityLogEntity)

    /** Auto-cleanup: logs older than 90 days are hard-deleted on app start. */
    @Query("DELETE FROM activity_logs WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int

    @Query(
        """
        SELECT * FROM activity_logs
        WHERE (:userId IS NULL OR user_id = :userId)
          AND timestamp BETWEEN :startMillis AND :endMillis
        ORDER BY timestamp ASC
        """,
    )
    suspend fun getForExport(
        userId: String?,
        startMillis: Long,
        endMillis: Long,
    ): List<ActivityLogEntity>
}
