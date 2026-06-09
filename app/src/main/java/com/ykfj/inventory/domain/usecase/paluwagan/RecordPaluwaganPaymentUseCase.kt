package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.PaluwaganPayment
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.repository.PaymentUpsert
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

/**
 * Records a paluwagan payment for a slot in the given round.
 *
 * Status is auto-computed:
 * - Payment date strictly before [Params.roundCollectionDate] → PAID
 * - Payment date on or after [Params.roundCollectionDate] → LATE
 *
 * If [Params.amountPaid] covers multiple rounds (≥ 2× [Params.contributionAmount]),
 * future rounds up to [Params.totalSlots] are automatically pre-paid with PREPAID status
 * using [Params.contributionAmount] each. If those round rows already exist (seeded by
 * advanceRound), they are updated in place; otherwise new rows are pre-created.
 *
 * If a pre-seeded UNPAID row exists (created by [AdvancePaluwaganRoundUseCase]),
 * it is updated in place. Otherwise a new payment row is inserted.
 *
 * Credit score: +1 for PAID, −2 for LATE.
 */
class RecordPaluwaganPaymentUseCase @Inject constructor(
    private val paluwaganRepository: PaluwaganRepository,
    private val customerRepository: CustomerRepository,
    private val logActivity: LogActivityUseCase,
) {

    data class Params(
        val groupId: String,
        val slotId: String,
        val customerId: String,
        val roundNumber: Int,
        val amountPaid: Double,
        /** The date the member actually paid (epoch millis, day precision from date picker). */
        val paymentDate: Long,
        val paymentMethod: PaymentMethod?,
        val notes: String?,
        val actorUserId: String,
        /**
         * The scheduled payout date for this round:
         * group.startDate + (roundNumber - 1) * frequencyDays * 86_400_000L
         */
        val roundCollectionDate: Long,
        /** Needed to split advance payments across future rounds correctly. */
        val contributionAmount: Double,
        /** Caps pre-pay loop so we don't create rows beyond the last round. */
        val totalSlots: Int,
        /** The group's current active round. Advance seeding only applies when paying the current round. */
        val currentGroupRound: Int,
        /** Used to compute per-round collection dates when seeding catch-up + current round payments. */
        val groupStartDate: Long,
        val frequencyDays: Int,
    )

    sealed class Result {
        object Success : Result()
        object AlreadyPaid : Result()
    }

    suspend operator fun invoke(params: Params): Result {
        val existing = paluwaganRepository.getPaymentForSlotRound(params.slotId, params.roundNumber)

        if (existing != null && existing.status != PaluwaganPaymentStatus.UNPAID) {
            return Result.AlreadyPaid
        }

        val mainStatus = if (params.paymentDate < params.roundCollectionDate)
            PaluwaganPaymentStatus.PAID
        else
            PaluwaganPaymentStatus.LATE

        val now = System.currentTimeMillis()

        val roundsCovered = if (params.contributionAmount > 0)
            (params.amountPaid / params.contributionAmount).toInt().coerceAtLeast(1)
        else 1

        // --- Read phase: collect all existing seed rows before any writes ---
        val seedExistingByRound = mutableMapOf<Int, com.ykfj.inventory.domain.model.PaluwaganPayment?>()
        if (roundsCovered > 1) {
            val isCatchUp = params.roundNumber < params.currentGroupRound
            val seedStartRound = if (isCatchUp) params.currentGroupRound else params.roundNumber + 1
            for (i in 0 until roundsCovered - 1) {
                val futureRound = seedStartRound + i
                if (futureRound > params.totalSlots) break
                seedExistingByRound[futureRound] =
                    paluwaganRepository.getPaymentForSlotRound(params.slotId, futureRound)
            }
        }

        // --- Build write list ---
        val upserts = mutableListOf<PaymentUpsert>()

        // Main round
        val mainPayment = PaluwaganPayment(
            id = existing?.id ?: UUID.randomUUID().toString(),
            groupId = params.groupId,
            slotId = params.slotId,
            roundNumber = params.roundNumber,
            amountPaid = params.amountPaid,
            paymentDate = params.paymentDate,
            status = mainStatus,
            notes = params.notes,
            paymentMethod = params.paymentMethod,
            createdAt = now,
            updatedAt = now,
        )
        upserts.add(PaymentUpsert(existingPaymentId = existing?.id, payment = mainPayment))

        // Seed rounds
        for ((futureRound, futureExisting) in seedExistingByRound) {
            // Skip rounds already paid/late/prepaid
            if (futureExisting != null && futureExisting.status != PaluwaganPaymentStatus.UNPAID) continue

            val futureRoundCollectionDate = params.groupStartDate +
                (futureRound.toLong() * params.frequencyDays - 1) * 86_400_000L
            val seedStatus = if (futureRound <= params.currentGroupRound) {
                if (params.paymentDate < futureRoundCollectionDate)
                    PaluwaganPaymentStatus.PAID else PaluwaganPaymentStatus.LATE
            } else {
                PaluwaganPaymentStatus.PREPAID
            }
            val seedNote = if (seedStatus == PaluwaganPaymentStatus.PREPAID)
                "Pre-paid from Round ${params.roundNumber} advance" else null

            val seedPayment = PaluwaganPayment(
                id = futureExisting?.id ?: UUID.randomUUID().toString(),
                groupId = params.groupId,
                slotId = params.slotId,
                roundNumber = futureRound,
                amountPaid = params.contributionAmount,
                paymentDate = params.paymentDate,
                status = seedStatus,
                notes = seedNote,
                paymentMethod = params.paymentMethod,
                createdAt = now,
                updatedAt = now,
            )
            upserts.add(PaymentUpsert(existingPaymentId = futureExisting?.id, payment = seedPayment))
        }

        // --- Single atomic write ---
        paluwaganRepository.recordPaymentAtomic(upserts)

        val creditDelta = if (mainStatus == PaluwaganPaymentStatus.PAID) 1 else -2
        customerRepository.adjustCreditScore(params.customerId, creditDelta)

        logActivity(
            userId = params.actorUserId,
            action = ActivityAction.PAYMENT,
            description = "Recorded $mainStatus paluwagan payment for slot round ${params.roundNumber}" +
                if (roundsCovered > 1) " (advance — $roundsCovered rounds)" else "",
            entityType = "paluwagan_payment",
            entityId = params.slotId,
        )
        return Result.Success
    }
}
