package com.ykfj.inventory.domain.model

import com.ykfj.inventory.data.local.db.enums.PaluwaganFrequency
import com.ykfj.inventory.data.local.db.enums.PaluwaganGroupStatus

/**
 * A paluwagan (rotating savings) group.
 *
 * Each round every slot pays [contributionAmount]; the slot whose
 * `position` equals [currentRound] collects the pot. [currentRound] is
 * 1-based (0 = not started).
 */
data class PaluwaganGroup(
    val id: String,
    val name: String,
    val contributionAmount: Double,
    val frequency: PaluwaganFrequency,
    val totalSlots: Int,
    val currentRound: Int,
    val status: PaluwaganGroupStatus,
    val startDate: Long,
    val notes: String?,
    val isArchived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
