package com.ykfj.inventory.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ykfj.inventory.data.local.db.entity.PaluwaganSlotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PaluwaganSlotDao {

    @Query(
        """
        SELECT * FROM paluwagan_slots
        WHERE group_id = :groupId AND is_deleted = 0
        ORDER BY position ASC
        """,
    )
    fun observeForGroup(groupId: String): Flow<List<PaluwaganSlotEntity>>

    @Query(
        """
        SELECT * FROM paluwagan_slots
        WHERE customer_id = :customerId AND is_deleted = 0
        """,
    )
    fun observeForCustomer(customerId: String): Flow<List<PaluwaganSlotEntity>>

    @Query("SELECT * FROM paluwagan_slots WHERE slot_id = :slotId LIMIT 1")
    suspend fun getById(slotId: String): PaluwaganSlotEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(slot: PaluwaganSlotEntity)

    @Update
    suspend fun update(slot: PaluwaganSlotEntity)

    @Query(
        """
        UPDATE paluwagan_slots SET position = :position, updated_at = :now
        WHERE slot_id = :slotId
        """,
    )
    suspend fun updatePosition(slotId: String, position: Int, now: Long)

    @Query("UPDATE paluwagan_slots SET is_deleted = 1, updated_at = :now WHERE slot_id = :slotId")
    suspend fun softDelete(slotId: String, now: Long)

    @Query("SELECT * FROM paluwagan_slots WHERE updated_at > :since")
    suspend fun getChangedSince(since: Long): List<PaluwaganSlotEntity>
}
