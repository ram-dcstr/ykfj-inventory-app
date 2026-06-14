package com.ykfj.inventory.domain.usecase.product

import com.ykfj.inventory.data.local.db.enums.DiscountType
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
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
 * Covers the money-sensitive branches of selling: quantity guards, the
 * Staff-can't-discount rule, and the 20%-of-profit discount cap — enforced here
 * (not just in the UI) because trade-in and future sync paths reuse this case.
 */
class MarkAsSoldUseCaseTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var soldRecordRepository: SoldRecordRepository
    private lateinit var userRepository: UserRepository
    private lateinit var useCase: MarkAsSoldUseCase

    @Before
    fun setUp() {
        productRepository = mockk(relaxUnitFun = true)
        soldRecordRepository = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        useCase = MarkAsSoldUseCase(productRepository, soldRecordRepository, userRepository)
    }

    private fun product(qty: Int): Product = mockk(relaxed = true) {
        every { quantity } returns qty
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    private fun params(
        quantity: Int = 1,
        soldPrice: Double = 1000.0,
        capitalPrice: Double = 600.0,
        discountAmount: Double = 0.0,
    ) = MarkAsSoldUseCase.Params(
        productId = "p1",
        actorUserId = "actor",
        quantity = quantity,
        soldPrice = soldPrice,
        capitalPrice = capitalPrice,
        discountAmount = discountAmount,
        discountType = DiscountType.NONE,
    )

    @Test
    fun `missing product returns ProductNotFound`() = runTest {
        coEvery { productRepository.getById("p1") } returns null
        assertEquals(MarkAsSoldUseCase.Result.ProductNotFound, useCase(params()))
    }

    @Test
    fun `selling more than available returns InsufficientQuantity`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 2)
        assertEquals(MarkAsSoldUseCase.Result.InsufficientQuantity, useCase(params(quantity = 3)))
    }

    @Test
    fun `staff cannot apply a discount`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        coEvery { userRepository.getById("actor") } returns user(UserRole.STAFF)
        assertEquals(
            MarkAsSoldUseCase.Result.DiscountNotAuthorized,
            useCase(params(discountAmount = 10.0)),
        )
    }

    @Test
    fun `unknown actor cannot apply a discount`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        coEvery { userRepository.getById("actor") } returns null
        assertEquals(
            MarkAsSoldUseCase.Result.DiscountNotAuthorized,
            useCase(params(discountAmount = 10.0)),
        )
    }

    @Test
    fun `discount above the cap is rejected with the allowed max`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        coEvery { userRepository.getById("actor") } returns user(UserRole.MANAGER)
        // profit = 1000 - 600 = 400, per-unit cap = 100; 150 exceeds it.
        val result = useCase(params(soldPrice = 1000.0, capitalPrice = 600.0, discountAmount = 150.0))
        assertEquals(MarkAsSoldUseCase.Result.DiscountExceedsCap(100.0), result)
    }

    @Test
    fun `discount exactly at the cap is allowed`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        assertTrue(useCase(params(discountAmount = 100.0)) is MarkAsSoldUseCase.Result.Success)
    }

    @Test
    fun `successful sale inserts a record and decrements quantity`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        val result = useCase(params(quantity = 2))
        assertTrue(result is MarkAsSoldUseCase.Result.Success)
        coVerify(exactly = 1) { soldRecordRepository.insert(any()) }
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", -2) }
    }

    @Test
    fun `staff may sell without a discount`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        // No discount -> no role lookup, staff allowed to sell.
        assertTrue(useCase(params(discountAmount = 0.0)) is MarkAsSoldUseCase.Result.Success)
    }
}
