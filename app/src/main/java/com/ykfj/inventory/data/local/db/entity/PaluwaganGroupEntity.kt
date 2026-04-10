package com.ykfj.inventory.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ykfj.inventory.data.local.db.enums.PaluwaganFrequency
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus

/**
 * A paluwagan (rotating savings) group.
 *
 * Each round, every slot pays [contribution_amount]; the slot whose
 * [PaluwaganSlotEntity.position] equals [current_round] collects the pot.
 * A customer may hold multiple slots (different positions) — they pay N×
 * and collect N× across the group's lifetime.
 */
@Entity(
    tableName = "paluwagan_groups",
    indices = [
        Index(value = ["name"]),
        Index(value = ["status"]),
        Index(value = ["updated_at"]),
        Index(value = ["is_archived"]),
        Index(value = ["is_deleted"]),
    ],
)
data class PaluwaganGroupEntity(
    @PrimaryKey val group_id: String,
    val name: String,
    val contribution_amount: Double,
    val frequency: PaluwaganFrequency,
    val total_slots: Int,
    /** 1-based. 0 = not started. */
    val current_round: Int = 0,
    val status: PaluwaganGroupStatus = PaluwaganGroupStatus.ACTIVE,
    val start_date: Long,
    val notes: String? = null,
    val is_archived: Boolean = false,
    val created_at: Long,
    val updated_at: Long,
    val is_deleted: Boolean = false,
)
