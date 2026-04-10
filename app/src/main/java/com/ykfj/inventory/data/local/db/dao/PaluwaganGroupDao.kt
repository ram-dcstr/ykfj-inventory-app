package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.PaluwaganGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaluwaganGroupDao {

    @Query(
        """
        SELECT * FROM paluwagan_groups
        WHERE is_deleted = 0 AND is_archived = 0 AND status = 'ACTIVE'
        ORDER BY start_date DESC
        """,
    )
    fun observeActive(): Flow<List<PaluwaganGroupEntity>>

    @Query(
        """
        SELECT * FROM paluwagan_groups
        WHERE is_deleted = 0 AND is_archived = 0
        ORDER BY start_date DESC
        """,
    )
    fun observeAll(): Flow<List<PaluwaganGroupEntity>>

    @Query(
        """
        SELECT * FROM paluwagan_groups
        WHERE is_deleted = 0 AND is_archived = 1
        ORDER BY start_date DESC
        """,
    )
    fun observeArchived(): Flow<List<PaluwaganGroupEntity>>

    @Query("SELECT * FROM paluwagan_groups WHERE group_id = :groupId LIMIT 1")
    suspend fun getById(groupId: String): PaluwaganGroupEntity?

    @Query("SELECT * FROM paluwagan_groups WHERE group_id = :groupId")
    fun observeById(groupId: String): Flow<PaluwaganGroupEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: PaluwaganGroupEntity)

    @Update
    suspend fun update(group: PaluwaganGroupEntity)

    @Query(
        """
        UPDATE paluwagan_groups
        SET current_round = :round, updated_at = :now WHERE group_id = :groupId
        """,
    )
    suspend fun updateCurrentRound(groupId: String, round: Int, now: Long)

    @Query(
        """
        UPDATE paluwagan_groups
        SET is_archived = 1, updated_at = :now WHERE group_id = :groupId
        """,
    )
    suspend fun archive(groupId: String, now: Long)

    @Query(
        """
        UPDATE paluwagan_groups
        SET is_deleted = 1, updated_at = :now WHERE group_id = :groupId
        """,
    )
    suspend fun softDelete(groupId: String, now: Long)

    @Query("SELECT * FROM paluwagan_groups WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<PaluwaganGroupEntity>
}
