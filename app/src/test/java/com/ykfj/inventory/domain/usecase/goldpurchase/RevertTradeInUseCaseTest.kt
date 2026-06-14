package com.ykfj.inventory.domain.usecase.goldpurchase

import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.GoldPurchaseRecord
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
 * Reverting a trade-in unwinds a sale + a gold purchase atomically. These cover
 * the guards before the transaction: Admin/Manager-only, and the three ways the
 * link can be missing (no purchase, no linked sale id, sale row gone). The atomic
 * unwind itself is inside db.withTransaction and left to instrumented coverage.
 */
class RevertTradeInUseCaseTest {

    private lateinit var db: YkfjDatabase
    private lateinit var goldPurchaseRepository: GoldPurchaseRepository
    private lateinit var soldRecordRepository: SoldRecordRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: RevertTradeInUseCase

    @Before
    fun setUp() {
        db = mockk(relaxed = true)
        goldPurchaseRepository = mockk(relaxUnitFun = true)
        soldRecordRepository = mockk(relaxUnitFun = true)
        productRepository = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = RevertTradeInUseCase(
            db, goldPurchaseRepository, soldRecordRepository, productRepository, userRepository, logActivity,
        )
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    private fun purchase(linkedSoldId: String?): GoldPurchaseRecord = mockk(relaxed = true) {
        every { linkedSoldRecordId } returns linkedSoldId
    }

    private fun params() =
        RevertTradeInUseCase.Params(goldPurchaseRecordId = "gp1", reason = "mistake", actorUserId = "actor")

    @Test
    fun `staff is not authorized`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.STAFF)
        assertEquals(RevertTradeInUseCase.Result.NotAuthorized, useCase(params()))
    }

    @Test
    fun `unknown actor is not authorized`() = runTest {
        coEvery { userRepository.getById("actor") } returns null
        assertEquals(RevertTradeInUseCase.Result.NotAuthorized, useCase(params()))
    }

    @Test
    fun `missing purchase returns NotFound`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { goldPurchaseRepository.getById("gp1") } returns null
        assertEquals(RevertTradeInUseCase.Result.NotFound, useCase(params()))
    }

    @Test
    fun `purchase without a linked sale returns NotFound`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.MANAGER)
        coEvery { goldPurchaseRepository.getById("gp1") } returns purchase(linkedSoldId = null)
        assertEquals(RevertTradeInUseCase.Result.NotFound, useCase(params()))
    }

    @Test
    fun `missing linked sold record returns NotFound`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { goldPurchaseRepository.getById("gp1") } returns purchase(linkedSoldId = "sold1")
        coEvery { soldRecordRepository.getById("sold1") } returns null
        assertEquals(RevertTradeInUseCase.Result.NotFound, useCase(params()))
    }
}
