package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
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
 * Damaging is always exactly one unit (Inventory-Rules.md). These lock the
 * availability guard and the status flip to DAMAGED only once the last unit is
 * gone (not SOLD).
 */
class MarkAsDamagedUseCaseTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var damagedRecordRepository: DamagedRecordRepository
    private lateinit var useCase: MarkAsDamagedUseCase

    @Before
    fun setUp() {
        productRepository = mockk(relaxUnitFun = true)
        damagedRecordRepository = mockk(relaxUnitFun = true)
        useCase = MarkAsDamagedUseCase(productRepository, damagedRecordRepository)
    }

    private fun product(qty: Int): Product = mockk(relaxed = true) {
        every { quantity } returns qty
    }

    private fun params() =
        MarkAsDamagedUseCase.Params(productId = "p1", actorUserId = "actor", reason = "broken clasp")

    @Test
    fun `missing product returns ProductNotFound`() = runTest {
        coEvery { productRepository.getById("p1") } returns null
        assertEquals(MarkAsDamagedUseCase.Result.ProductNotFound, useCase(params()))
    }

    @Test
    fun `no units available returns NoUnitsAvailable`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 0)
        assertEquals(MarkAsDamagedUseCase.Result.NoUnitsAvailable, useCase(params()))
    }

    @Test
    fun `damaging one of several units records it without flipping status`() = runTest {
        coEvery { productRepository.getById("p1") } returnsMany listOf(product(qty = 3), product(qty = 2))
        assertTrue(useCase(params()) is MarkAsDamagedUseCase.Result.Success)
        coVerify(exactly = 1) { damagedRecordRepository.insert(any()) }
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", -1) }
        coVerify(exactly = 0) { productRepository.setStatus(any(), any()) }
    }

    @Test
    fun `damaging the last unit flips status to DAMAGED`() = runTest {
        coEvery { productRepository.getById("p1") } returnsMany listOf(product(qty = 1), product(qty = 0))
        assertTrue(useCase(params()) is MarkAsDamagedUseCase.Result.Success)
        coVerify(exactly = 1) { productRepository.setStatus("p1", ProductStatus.DAMAGED) }
    }
}
