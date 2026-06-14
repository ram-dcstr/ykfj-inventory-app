package com.ykfj.inventory.domain.usecase.goldpurchase

import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.GoldPurchaseRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Trade-in is a sale paid (partly) in scrap gold. These cover the validation
 * guards that run before the atomic write — at-least-one valid item, quantity,
 * and the same Staff-can't-discount / 20%-of-profit cap as a plain sale. The
 * happy-path atomic insert runs inside db.withTransaction and is left to
 * instrumented coverage.
 */
class SellWithTradeInUseCaseTest {

    private lateinit var db: YkfjDatabase
    private lateinit var soldRecordRepository: SoldRecordRepository
    private lateinit var goldPurchaseRepository: GoldPurchaseRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: SellWithTradeInUseCase

    @Before
    fun setUp() {
        db = mockk(relaxed = true)
        soldRecordRepository = mockk(relaxUnitFun = true)
        goldPurchaseRepository = mockk(relaxUnitFun = true)
        productRepository = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = SellWithTradeInUseCase(
            db, soldRecordRepository, goldPurchaseRepository,
            productRepository, userRepository, logActivity,
        )
    }

    private fun product(qty: Int): Product = mockk(relaxed = true) {
        every { quantity } returns qty
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    private fun item(weight: Double = 5.0, rate: Double = 2000.0) =
        AddGoldPurchaseUseCase.ItemDraft(description = "scrap", weightGrams = weight, buyRatePerGram = rate)

    private fun params(
        quantity: Int = 1,
        soldPrice: Double = 1000.0,
        capitalPrice: Double = 600.0,
        discountAmount: Double = 0.0,
        items: List<AddGoldPurchaseUseCase.ItemDraft> = listOf(item()),
    ) = SellWithTradeInUseCase.Params(
        productId = "p1", actorUserId = "actor", quantity = quantity,
        soldPrice = soldPrice, capitalPrice = capitalPrice,
        discountAmount = discountAmount, tradeInItems = items,
    )

    @Test
    fun `no trade-in items returns NoItems`() = runTest {
        assertEquals(SellWithTradeInUseCase.Result.NoItems, useCase(params(items = emptyList())))
    }

    @Test
    fun `an item with non-positive weight or rate is invalid`() = runTest {
        assertEquals(
            SellWithTradeInUseCase.Result.InvalidItem,
            useCase(params(items = listOf(item(weight = 0.0)))),
        )
        assertEquals(
            SellWithTradeInUseCase.Result.InvalidItem,
            useCase(params(items = listOf(item(rate = 0.0)))),
        )
    }

    @Test
    fun `missing product returns ProductNotFound`() = runTest {
        coEvery { productRepository.getById("p1") } returns null
        assertEquals(SellWithTradeInUseCase.Result.ProductNotFound, useCase(params()))
    }

    @Test
    fun `selling more than available returns InsufficientQuantity`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 1)
        assertEquals(SellWithTradeInUseCase.Result.InsufficientQuantity, useCase(params(quantity = 2)))
    }

    @Test
    fun `staff cannot apply a discount on a trade-in`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        coEvery { userRepository.getById("actor") } returns user(UserRole.STAFF)
        assertEquals(
            SellWithTradeInUseCase.Result.DiscountNotAuthorized,
            useCase(params(discountAmount = 10.0)),
        )
    }

    @Test
    fun `discount above the cap is rejected with the allowed max`() = runTest {
        coEvery { productRepository.getById("p1") } returns product(qty = 5)
        coEvery { userRepository.getById("actor") } returns user(UserRole.MANAGER)
        // profit = 1000 - 600 = 400, per-unit cap = 100; 150 exceeds it.
        assertEquals(
            SellWithTradeInUseCase.Result.DiscountExceedsCap(100.0),
            useCase(params(discountAmount = 150.0)),
        )
    }
}
