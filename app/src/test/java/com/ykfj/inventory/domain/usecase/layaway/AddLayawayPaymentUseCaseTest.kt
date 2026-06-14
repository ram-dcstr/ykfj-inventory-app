package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Layaway payments touch money, inventory status, and customer credit at once.
 * These lock the completion threshold (paid >= price × qty), the product flip on
 * completion, and the credit-score rules — including that a late payment always
 * costs −10 even when it completes the layaway.
 */
class AddLayawayPaymentUseCaseTest {

    private lateinit var layawayRepository: LayawayRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: AddLayawayPaymentUseCase

    @Before
    fun setUp() {
        layawayRepository = mockk(relaxUnitFun = true)
        productRepository = mockk(relaxUnitFun = true)
        customerRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = AddLayawayPaymentUseCase(layawayRepository, productRepository, customerRepository, logActivity)
    }

    private fun record(
        status: LayawayStatus = LayawayStatus.ACTIVE,
        totalPaid: Double = 0.0,
        unitPrice: Double = 1000.0,
        quantity: Int = 1,
        dueDate: Long? = null,
    ) = LayawayRecord(
        id = "lay1", productId = "p1", customerId = "c1", createdBy = "actor",
        quantity = quantity, unitPrice = unitPrice, totalPaid = totalPaid,
        dueDate = dueDate, status = status, completionDate = null,
        forfeitedAmount = null, isArchived = false, createdAt = 0L, updatedAt = 0L,
    )

    private fun product(qty: Int): Product = mockk(relaxed = true) {
        every { quantity } returns qty
        every { id } returns "p1" // use case flips status via product.id
    }

    private fun params(amount: Double) =
        AddLayawayPaymentUseCase.Params(layawayId = "lay1", amount = amount, notes = null, actorUserId = "actor")

    @Test
    fun `missing record returns RecordNotFound`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns null
        assertEquals(AddLayawayPaymentUseCase.Result.RecordNotFound, useCase(params(100.0)))
    }

    @Test
    fun `payment on a non-active layaway is rejected`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns record(status = LayawayStatus.COMPLETED)
        assertEquals(AddLayawayPaymentUseCase.Result.AlreadyCompleted, useCase(params(100.0)))
    }

    @Test
    fun `partial on-time payment records it and adds one credit point`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns record(totalPaid = 0.0, unitPrice = 1000.0)
        assertEquals(AddLayawayPaymentUseCase.Result.Success, useCase(params(300.0)))
        coVerify(exactly = 1) { layawayRepository.addPayment(any()) }
        coVerify(exactly = 0) { layawayRepository.markCompleted(any(), any()) }
        coVerify(exactly = 1) { customerRepository.adjustCreditScore("c1", 1) }
    }

    @Test
    fun `payment reaching the price completes the layaway and flips the product`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns record(totalPaid = 700.0, unitPrice = 1000.0)
        coEvery { productRepository.getById("p1") } returns product(qty = 0)
        assertEquals(AddLayawayPaymentUseCase.Result.Success, useCase(params(300.0)))
        coVerify(exactly = 1) { layawayRepository.markCompleted("lay1", any()) }
        coVerify(exactly = 1) { productRepository.setStatus("p1", ProductStatus.SOLD) }
        coVerify(exactly = 1) { customerRepository.adjustCreditScore("c1", 10) }
    }

    @Test
    fun `a late payment costs ten credit points even when it completes`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns
            record(totalPaid = 700.0, unitPrice = 1000.0, dueDate = 1L) // due in 1970 → overdue
        coEvery { productRepository.getById("p1") } returns product(qty = 0)
        assertEquals(AddLayawayPaymentUseCase.Result.Success, useCase(params(300.0)))
        coVerify(exactly = 1) { layawayRepository.markCompleted("lay1", any()) }
        coVerify(exactly = 1) { customerRepository.adjustCreditScore("c1", -10) }
    }

    @Test
    fun `overpayment still completes the layaway`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns record(totalPaid = 900.0, unitPrice = 1000.0)
        coEvery { productRepository.getById("p1") } returns product(qty = 0)
        assertEquals(AddLayawayPaymentUseCase.Result.Success, useCase(params(500.0)))
        coVerify(exactly = 1) { layawayRepository.markCompleted("lay1", any()) }
    }
}
