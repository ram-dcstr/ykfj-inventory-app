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
 * the member's next unpaid rounds are filled in ascending order — earliest missed
 * rounds first — up to [Params.totalSlots], one [Params.contributionAmount] each.
 * Already-covered rounds are skipped. Each filled round's status is PAID/LATE if it is
 * at or before the current group round (its collection date has arrived), or PREPAID if
 * it is a future round. This lets a member who missed everything settle the full amount
 * at the final round and have every prior round cleared in order.
 *
 * The amount is split so the row totals reconcile exactly to [Params.amountPaid]: each
 * filled future round holds one contribution, and the main round keeps the remainder
 * (no double counting).
 *
 * If a pre-seeded UNPAID row exists (created by [AdvancePaluwaganRoundUseCase]),
 * it is updated in place. Otherwise a new payment row is inserted.
 *
 * Credit score: applied once per round settled in this session (main round + each
 * seed) — +1 for an on-time PAID or ahead-of-schedule PREPAID round, −2 for a LATE
 * one. A chronic late payer clearing several missed rounds at once is penalised per
 * round, not just once.
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

        // A round paid before the group has reached it is settled ahead of schedule →
        // PREPAID, matching the seed-round labelling below. This covers the "pay ahead"
        // button, which records a single future round directly as the main payment.
        // Otherwise it's PAID when settled before its own collection date, LATE after.
        val mainStatus = when {
            params.roundNumber > params.currentGroupRound -> PaluwaganPaymentStatus.PREPAID
            params.paymentDate < params.roundCollectionDate -> PaluwaganPaymentStatus.PAID
            else -> PaluwaganPaymentStatus.LATE
        }

        val now = System.currentTimeMillis()

        val roundsCovered = if (params.contributionAmount > 0)
            (params.amountPaid / params.contributionAmount).toInt().coerceAtLeast(1)
        else 1

        // --- Read phase: collect the member's next unpaid rounds after the main round ---
        // We fill the EARLIEST missed/open rounds first (ascending) and skip rounds
        // already covered, so a late payer settles their first misses before paying any
        // round ahead. This also lets someone who missed everything pay the full amount
        // at the final round and have every prior round filled in order.
        val seedExistingByRound = linkedMapOf<Int, com.ykfj.inventory.domain.model.PaluwaganPayment?>()
        if (roundsCovered > 1) {
            var r = params.roundNumber + 1
            while (r <= params.totalSlots && seedExistingByRound.size < roundsCovered - 1) {
                val rowForRound = paluwaganRepository.getPaymentForSlotRound(params.slotId, r)
                if (rowForRound == null || rowForRound.status == PaluwaganPaymentStatus.UNPAID) {
                    seedExistingByRound[r] = rowForRound
                }
                r++
            }
        }

        // --- Build seed rounds first, so we know how many we actually create ---
        // Each seed round holds exactly one contributionAmount.
        val seedUpserts = mutableListOf<PaymentUpsert>()
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
            seedUpserts.add(PaymentUpsert(existingPaymentId = futureExisting?.id, payment = seedPayment))
        }

        // The advance portion is allocated to the seed rows (one contributionAmount
        // each). The main round keeps only the remainder, so the sum across ALL rows
        // equals exactly amountPaid. Previously the main round stored the full
        // amountPaid *and* each seed stored contributionAmount → the advance was
        // counted twice, inflating the member's total beyond the group's max.
        val seedsTotal = params.contributionAmount * seedUpserts.size
        val mainAmount = (params.amountPaid - seedsTotal).coerceAtLeast(0.0)

        // --- Build write list (main round first, then seeds) ---
        val upserts = mutableListOf<PaymentUpsert>()

        val mainPayment = PaluwaganPayment(
            id = existing?.id ?: UUID.randomUUID().toString(),
            groupId = params.groupId,
            slotId = params.slotId,
            roundNumber = params.roundNumber,
            amountPaid = mainAmount,
            paymentDate = params.paymentDate,
            status = mainStatus,
            notes = params.notes
                ?: if (mainStatus == PaluwaganPaymentStatus.PREPAID) "Paid ahead of schedule" else null,
            paymentMethod = params.paymentMethod,
            createdAt = now,
            updatedAt = now,
        )
        upserts.add(PaymentUpsert(existingPaymentId = existing?.id, payment = mainPayment))
        upserts.addAll(seedUpserts)

        // --- Single atomic write ---
        paluwaganRepository.recordPaymentAtomic(upserts)

        // Credit score moves once per round actually settled in this session — the
        // main round plus every catch-up/advance seed. A member clearing four missed
        // rounds in one lump takes the LATE penalty four times, not once; paying on
        // time or ahead of schedule earns +1 per round.
        val creditDelta = upserts.sumOf { upsert ->
            val delta: Int = when (upsert.payment.status) {
                PaluwaganPaymentStatus.PAID, PaluwaganPaymentStatus.PREPAID -> 1
                PaluwaganPaymentStatus.LATE -> -2
                else -> 0
            }
            delta
        }
        if (creditDelta != 0) customerRepository.adjustCreditScore(params.customerId, creditDelta)

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
