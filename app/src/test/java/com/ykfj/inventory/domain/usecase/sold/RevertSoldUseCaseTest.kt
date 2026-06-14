package com.ykfj.inventory.domain.usecase.sold

import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.PaymentMethod
import com.ykfj.inventory.domain.model.SoldRecord
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Reverting a sale puts gold back into stock, so the quantity math and the
 * full-vs-partial soft-delete behaviour have to be exact. Also covers unlinking
 * a layaway-completed sale.
 */
class RevertSoldUseCaseTest {

    private lateinit var soldRecordRepository: SoldRecordRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var layawayRepository: LayawayRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: RevertSoldUseCase

    @Before
    fun setUp() {
        soldRecordRepository = mockk(relaxUnitFun = true)
        productRepository = mockk(relaxUnitFun = true)
        layawayRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = RevertSoldUseCase(soldRecordRepository, productRepository, layawayRepository, logActivity)
    }

    private fun record(
        quantity: Int,
        notes: String? = null,
        linkedLayawayId: String? = null,
    ) = SoldRecord(
        id = "sold1", productId = "p1", customerId = null, soldBy = "actor",
        quantity = quantity, soldPrice = 1000.0, capitalPrice = 600.0,
        discountAmount = 0.0, discountType = DiscountType.NONE, soldDate = 0L,
        notes = notes, paymentMethod = PaymentMethod.CASH, linkedLayawayId = linkedLayawayId,
        isArchived = false, createdAt = 0L, updatedAt = 0L,
    )

    private fun params(quantity: Int, soldId: String = "sold1") =
        RevertSoldUseCase.Params(soldId = soldId, quantity = quantity, reason = "wrong item", actorUserId = "actor")

    @Test
    fun `missing record returns RecordNotFound`() = runTest {
        coEvery { soldRecordRepository.getById("sold1") } returns null
        assertEquals(RevertSoldUseCase.Result.RecordNotFound, useCase(params(quantity = 1)))
    }

    @Test
    fun `full revert soft-deletes the record and restores the full quantity`() = runTest {
        coEvery { soldRecordRepository.getById("sold1") } returns record(quantity = 3)
        assertEquals(RevertSoldUseCase.Result.Success, useCase(params(quantity = 3)))
        coVerify(exactly = 1) { soldRecordRepository.softDelete("sold1") }
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", 3) }
    }

    @Test
    fun `partial revert reduces the record quantity and does not soft-delete`() = runTest {
        coEvery { soldRecordRepository.getById("sold1") } returns record(quantity = 5)
        assertEquals(RevertSoldUseCase.Result.Success, useCase(params(quantity = 2)))
        coVerify(exactly = 1) { soldRecordRepository.update(match { it.quantity == 3 }) }
        coVerify(exactly = 0) { soldRecordRepository.softDelete(any()) }
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", 2) }
    }

    @Test
    fun `revert quantity is clamped to the record quantity`() = runTest {
        coEvery { soldRecordRepository.getById("sold1") } returns record(quantity = 2)
        // Asking to revert 5 of a 2-unit sale reverts 2 (a full revert).
        assertEquals(RevertSoldUseCase.Result.Success, useCase(params(quantity = 5)))
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", 2) }
        coVerify(exactly = 1) { soldRecordRepository.softDelete("sold1") }
    }

    @Test
    fun `reverting a layaway-completed sale cancels the linked layaway`() = runTest {
        coEvery { soldRecordRepository.getById("sold1") } returns record(quantity = 1, linkedLayawayId = "lay9")
        useCase(params(quantity = 1))
        coVerify(exactly = 1) { layawayRepository.markCancelled("lay9", any()) }
    }

    @Test
    fun `reverting via the legacy notes marker still cancels the layaway`() = runTest {
        coEvery { soldRecordRepository.getById("sold1") } returns
            record(quantity = 1, notes = "layaway_complete:lay7")
        useCase(params(quantity = 1))
        coVerify(exactly = 1) { layawayRepository.markCancelled("lay7", any()) }
    }

    @Test
    fun `reverting a normal sale does not touch any layaway`() = runTest {
        coEvery { soldRecordRepository.getById("sold1") } returns record(quantity = 1)
        useCase(params(quantity = 1))
        coVerify(exactly = 0) { layawayRepository.markCancelled(any(), any()) }
    }
}
