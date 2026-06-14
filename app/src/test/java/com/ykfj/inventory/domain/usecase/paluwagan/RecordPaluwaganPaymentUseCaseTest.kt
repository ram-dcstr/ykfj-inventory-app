package com.ykfj.inventory.domain.usecase.paluwagan

import com.ykfj.inventory.data.local.db.enums.PaluwaganPaymentStatus
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.repository.PaymentUpsert
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class RecordPaluwaganPaymentUseCaseTest {

    private lateinit var paluwaganRepository: PaluwaganRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: RecordPaluwaganPaymentUseCase

    private val contribution = 1000.0
    private val totalSlots = 5

    @Before
    fun setUp() {
        paluwaganRepository = mockk(relaxUnitFun = true)
        customerRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = RecordPaluwaganPaymentUseCase(paluwaganRepository, customerRepository, logActivity)
    }

    private fun params(roundNumber: Int, amountPaid: Double, currentGroupRound: Int) =
        RecordPaluwaganPaymentUseCase.Params(
            groupId = "g1",
            slotId = "s1",
            customerId = "c1",
            roundNumber = roundNumber,
            amountPaid = amountPaid,
            paymentDate = 1_000L,
            paymentMethod = null,
            notes = null,
            actorUserId = "u1",
            roundCollectionDate = 10_000L, // payment before deadline → PAID
            contributionAmount = contribution,
            totalSlots = totalSlots,
            currentGroupRound = currentGroupRound,
            groupStartDate = 0L,
            frequencyDays = 7,
        )

    /** The core money invariant: the sum of all written rows must equal what was paid. */
    @Test
    fun `advance payment does not double-count - row totals equal amount paid`() = runTest {
        coEvery { paluwaganRepository.getPaymentForSlotRound(any(), any()) } returns null
        val captured = slot<List<PaymentUpsert>>()
        coEvery { paluwaganRepository.recordPaymentAtomic(capture(captured)) } returns Unit

        // Pay 5000 at round 1 of a 5-round @ 1000 group → should cover all 5 rounds, total 5000.
        useCase(params(roundNumber = 1, amountPaid = 5000.0, currentGroupRound = 1))

        val rows = captured.captured
        val sum = rows.sumOf { it.payment.amountPaid }
        assertEquals("rows must reconcile to amount paid", 5000.0, sum, 0.001)
        assertEquals("should write one row per round", 5, rows.size)
        // Each future round is a 1000 PREPAID seed; the rest sits on the main round.
        val prepaid = rows.filter { it.payment.status == PaluwaganPaymentStatus.PREPAID }
        assertEquals(4, prepaid.size)
        prepaid.forEach { assertEquals(1000.0, it.payment.amountPaid, 0.001) }
        val main = rows.first { it.payment.roundNumber == 1 }
        assertEquals(1000.0, main.payment.amountPaid, 0.001)
    }

    @Test
    fun `two-round advance stores one contribution on main and one on seed`() = runTest {
        coEvery { paluwaganRepository.getPaymentForSlotRound(any(), any()) } returns null
        val captured = slot<List<PaymentUpsert>>()
        coEvery { paluwaganRepository.recordPaymentAtomic(capture(captured)) } returns Unit

        // Pay 2000 at round 2 → covers rounds 2 and 3.
        useCase(params(roundNumber = 2, amountPaid = 2000.0, currentGroupRound = 2))

        val rows = captured.captured
        assertEquals(2000.0, rows.sumOf { it.payment.amountPaid }, 0.001)
        assertEquals(1000.0, rows.first { it.payment.roundNumber == 2 }.payment.amountPaid, 0.001)
        assertEquals(1000.0, rows.first { it.payment.roundNumber == 3 }.payment.amountPaid, 0.001)
    }

    @Test
    fun `single round payment writes exactly one row`() = runTest {
        coEvery { paluwaganRepository.getPaymentForSlotRound(any(), any()) } returns null
        val captured = slot<List<PaymentUpsert>>()
        coEvery { paluwaganRepository.recordPaymentAtomic(capture(captured)) } returns Unit

        useCase(params(roundNumber = 1, amountPaid = 1000.0, currentGroupRound = 1))

        val rows = captured.captured
        assertEquals(1, rows.size)
        assertEquals(1000.0, rows.single().payment.amountPaid, 0.001)
    }

    @Test
    fun `late member pays full amount at final round - fills all missed rounds earliest-first`() = runTest {
        // Member missed rounds 1-4; group is now at the final round (5). No rows seeded yet.
        coEvery { paluwaganRepository.getPaymentForSlotRound(any(), any()) } returns null
        val captured = slot<List<PaymentUpsert>>()
        coEvery { paluwaganRepository.recordPaymentAtomic(capture(captured)) } returns Unit

        // Pay 5000 starting at the earliest unpaid round (1), while currentGroupRound = 5.
        useCase(params(roundNumber = 1, amountPaid = 5000.0, currentGroupRound = 5))

        val rows = captured.captured
        // Every round 1..5 gets exactly one row, in order, no skips.
        assertEquals(listOf(1, 2, 3, 4, 5), rows.map { it.payment.roundNumber }.sorted())
        assertEquals("rows reconcile to amount paid", 5000.0, rows.sumOf { it.payment.amountPaid }, 0.001)
        // Rounds at/before the current group round are PAID or LATE — never PREPAID,
        // since their collection dates have already arrived.
        rows.forEach {
            assert(it.payment.status != PaluwaganPaymentStatus.PREPAID) {
                "round ${it.payment.roundNumber} should not be PREPAID when <= current round"
            }
        }
    }

    @Test
    fun `advance never seeds rounds beyond totalSlots`() = runTest {
        coEvery { paluwaganRepository.getPaymentForSlotRound(any(), any()) } returns null
        val captured = slot<List<PaymentUpsert>>()
        coEvery { paluwaganRepository.recordPaymentAtomic(capture(captured)) } returns Unit

        // Pay 3000 at round 4 of 5 → can only cover rounds 4 and 5 (2 rounds); round 6 doesn't exist.
        useCase(params(roundNumber = 4, amountPaid = 3000.0, currentGroupRound = 4))

        val rows = captured.captured
        assertEquals("no round beyond totalSlots", emptyList<Int>(), rows.map { it.payment.roundNumber }.filter { it > totalSlots })
        // The excess that can't be allocated to a real round stays on the main round,
        // so no money is silently lost: total still equals amount paid.
        assertEquals(3000.0, rows.sumOf { it.payment.amountPaid }, 0.001)
    }
}
