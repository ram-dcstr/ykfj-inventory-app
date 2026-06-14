package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.StockAdjustmentReason
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.StockAdjustmentRepository
import com.ykfj.inventory.domain.repository.UserRepository
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
 * Stock write-offs remove gold from inventory, so they're Admin-only and the
 * quantity guards (invalid amount, over-stock) plus the auto-delete-at-zero
 * behaviour must hold.
 */
class AdjustStockUseCaseTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var stockAdjustmentRepository: StockAdjustmentRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: AdjustStockUseCase

    @Before
    fun setUp() {
        productRepository = mockk(relaxUnitFun = true)
        stockAdjustmentRepository = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = AdjustStockUseCase(productRepository, stockAdjustmentRepository, userRepository, logActivity)
    }

    private fun product(qty: Int): Product = mockk(relaxed = true) {
        every { quantity } returns qty
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    private fun params(quantity: Int) = AdjustStockUseCase.Params(
        productId = "p1",
        actorUserId = "actor",
        quantity = quantity,
        reason = StockAdjustmentReason.LOST,
        notes = null,
    )

    @Test
    fun `non-admin actor is not authorized`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.MANAGER)
        assertEquals(AdjustStockUseCase.Result.NotAuthorized, useCase(params(quantity = 1)))
    }

    @Test
    fun `unknown actor is not authorized`() = runTest {
        coEvery { userRepository.getById("actor") } returns null
        assertEquals(AdjustStockUseCase.Result.NotAuthorized, useCase(params(quantity = 1)))
    }

    @Test
    fun `missing product returns ProductNotFound`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { productRepository.getById("p1") } returns null
        assertEquals(AdjustStockUseCase.Result.ProductNotFound, useCase(params(quantity = 1)))
    }

    @Test
    fun `quantity below one is invalid`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        assertEquals(AdjustStockUseCase.Result.InvalidQuantity, useCase(params(quantity = 0)))
    }

    @Test
    fun `removing more than stock returns InsufficientStock with the available count`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { productRepository.getById("p1") } returns product(qty = 2)
        assertEquals(AdjustStockUseCase.Result.InsufficientStock(2), useCase(params(quantity = 3)))
    }

    @Test
    fun `write-off inserts a record, decrements stock, and keeps a product with units left`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        assertEquals(AdjustStockUseCase.Result.Success, useCase(params(quantity = 2)))
        coVerify(exactly = 1) { stockAdjustmentRepository.insert(any()) }
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", -2) }
        coVerify(exactly = 0) { productRepository.delete(any()) }
    }

    @Test
    fun `writing off the last units deletes the product`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { productRepository.getById("p1") } returns product(qty = 2)
        assertEquals(AdjustStockUseCase.Result.Success, useCase(params(quantity = 2)))
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", -2) }
        coVerify(exactly = 1) { productRepository.delete("p1") }
    }
}
