package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Putting a product on layaway reserves stock, so it guards quantity and enforces
 * the "only one active layaway per product" rule (Inventory-Rules.md). Also checks
 * the status flip to LAYAWAY once the last free unit is reserved.
 */
class MarkAsLayawayUseCaseTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var layawayRepository: LayawayRepository
    private lateinit var useCase: MarkAsLayawayUseCase

    @Before
    fun setUp() {
        productRepository = mockk(relaxUnitFun = true)
        layawayRepository = mockk(relaxUnitFun = true)
        useCase = MarkAsLayawayUseCase(productRepository, layawayRepository)
    }

    private fun product(qty: Int): Product = mockk(relaxed = true) {
        every { quantity } returns qty
    }

    private fun params(quantity: Int = 1) = MarkAsLayawayUseCase.Params(
        productId = "p1", actorUserId = "actor", customerId = "c1",
        quantity = quantity, unitPrice = 1000.0,
    )

    @Test
    fun `missing product returns ProductNotFound`() = runTest {
        coEvery { productRepository.getById("p1") } returns null
        assertEquals(MarkAsLayawayUseCase.Result.ProductNotFound, useCase(params()))
    }

    @Test
    fun `reserving more than available returns InsufficientQuantity`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 1)
        assertEquals(MarkAsLayawayUseCase.Result.InsufficientQuantity, useCase(params(quantity = 2)))
    }

    @Test
    fun `a second active layaway is blocked`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        val existing: LayawayRecord = mockk(relaxed = true) { every { id } returns "existing-lay" }
        coEvery { layawayRepository.getActiveForProduct("p1") } returns existing
        assertEquals(
            MarkAsLayawayUseCase.Result.ActiveLayawayExists("existing-lay"),
            useCase(params()),
        )
        coVerify(exactly = 0) { layawayRepository.insert(any()) }
    }

    @Test
    fun `reserving when units remain inserts the layaway and does not flip status`() = runTest {
        // First getById -> 3 units; after the decrement -> 2 units (still free stock).
        coEvery { productRepository.getById("p1") } returnsMany listOf(product(qty = 3), product(qty = 2))
        coEvery { layawayRepository.getActiveForProduct("p1") } returns null
        assertTrue(useCase(params(quantity = 1)) is MarkAsLayawayUseCase.Result.Success)
        coVerify(exactly = 1) { layawayRepository.insert(any()) }
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", -1) }
        coVerify(exactly = 0) { productRepository.setStatus(any(), any()) }
    }

    @Test
    fun `reserving the last unit flips the product to LAYAWAY`() = runTest {
        // First getById -> 1 unit; after the decrement -> 0 units left.
        coEvery { productRepository.getById("p1") } returnsMany listOf(product(qty = 1), product(qty = 0))
        coEvery { layawayRepository.getActiveForProduct("p1") } returns null
        assertTrue(useCase(params(quantity = 1)) is MarkAsLayawayUseCase.Result.Success)
        coVerify(exactly = 1) { productRepository.setStatus("p1", ProductStatus.LAYAWAY) }
    }
}
