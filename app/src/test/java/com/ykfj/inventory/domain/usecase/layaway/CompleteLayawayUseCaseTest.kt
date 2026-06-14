package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.data.local.db.enums.ProductStatus
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.model.Product
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
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
 * Completing a layaway flips the product status and drops a SoldRecord into the
 * archive (linked back to the layaway so a revert can unwind it). These lock the
 * active guard, the status flip, and that the sold record carries the link.
 */
class CompleteLayawayUseCaseTest {

    private lateinit var layawayRepository: LayawayRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var soldRecordRepository: SoldRecordRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: CompleteLayawayUseCase

    @Before
    fun setUp() {
        layawayRepository = mockk(relaxUnitFun = true)
        productRepository = mockk(relaxUnitFun = true)
        soldRecordRepository = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = CompleteLayawayUseCase(
            layawayRepository, productRepository, soldRecordRepository, userRepository, logActivity,
        )
        // Default to an authorized admin; the NotAuthorized test overrides this.
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    private fun record(status: LayawayStatus = LayawayStatus.ACTIVE) = LayawayRecord(
        id = "lay1", productId = "p1", customerId = "c1", createdBy = "actor",
        quantity = 1, unitPrice = 1000.0, totalPaid = 1000.0,
        dueDate = null, status = status, completionDate = null,
        forfeitedAmount = null, isArchived = false, createdAt = 0L, updatedAt = 0L,
    )

    private fun product(qty: Int): Product = mockk(relaxed = true) {
        every { quantity } returns qty
        every { id } returns "p1"
    }

    private fun params() = CompleteLayawayUseCase.Params(layawayId = "lay1", actorUserId = "actor")

    @Test
    fun `non-admin actor is not authorized`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.MANAGER)
        assertEquals(CompleteLayawayUseCase.Result.NotAuthorized, useCase(params()))
    }

    @Test
    fun `missing record returns RecordNotFound`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns null
        assertEquals(CompleteLayawayUseCase.Result.RecordNotFound, useCase(params()))
    }

    @Test
    fun `completing a non-active layaway returns NotActive`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns record(status = LayawayStatus.COMPLETED)
        assertEquals(CompleteLayawayUseCase.Result.NotActive, useCase(params()))
    }

    @Test
    fun `completion marks it complete, flips the product to SOLD, and links the sold record`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns record()
        coEvery { productRepository.getById("p1") } returns product(qty = 0)
        assertEquals(CompleteLayawayUseCase.Result.Success, useCase(params()))
        coVerify(exactly = 1) { layawayRepository.markCompleted("lay1", any()) }
        coVerify(exactly = 1) { productRepository.setStatus("p1", ProductStatus.SOLD) }
        coVerify(exactly = 1) { soldRecordRepository.insert(match { it.linkedLayawayId == "lay1" }) }
    }

    @Test
    fun `a product with units left becomes AVAILABLE again`() = runTest {
        coEvery { layawayRepository.getById("lay1") } returns record()
        coEvery { productRepository.getById("p1") } returns product(qty = 1)
        assertEquals(CompleteLayawayUseCase.Result.Success, useCase(params()))
        coVerify(exactly = 1) { productRepository.setStatus("p1", ProductStatus.AVAILABLE) }
    }
}
