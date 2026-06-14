package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.LayawayStatus
import com.ykfj.inventory.domain.model.LayawayRecord
import com.ykfj.inventory.domain.repository.CustomerRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Cancelling a layaway is Admin-only and forfeits payments while restoring the
 * reserved stock and docking the customer's credit. These lock the role gate,
 * the active-status guard, and the three side effects.
 */
class CancelLayawayUseCaseTest {

    private lateinit var layawayRepository: LayawayRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var customerRepository: CustomerRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: CancelLayawayUseCase

    @Before
    fun setUp() {
        layawayRepository = mockk(relaxUnitFun = true)
        productRepository = mockk(relaxUnitFun = true)
        customerRepository = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = CancelLayawayUseCase(
            layawayRepository, productRepository, customerRepository, userRepository, logActivity,
        )
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    private fun record(status: LayawayStatus = LayawayStatus.ACTIVE, quantity: Int = 1) = LayawayRecord(
        id = "lay1", productId = "p1", customerId = "c1", createdBy = "actor",
        quantity = quantity, unitPrice = 1000.0, totalPaid = 500.0,
        dueDate = null, status = status, completionDate = null,
        forfeitedAmount = null, isArchived = false, createdAt = 0L, updatedAt = 0L,
    )

    private fun params() = CancelLayawayUseCase.Params(layawayId = "lay1", actorUserId = "actor")

    @Test
    fun `non-admin is not authorized`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.MANAGER)
        assertEquals(CancelLayawayUseCase.Result.NotAuthorized, useCase(params()))
    }

    @Test
    fun `unknown actor is not authorized`() = runTest {
        coEvery { userRepository.getById("actor") } returns null
        assertEquals(CancelLayawayUseCase.Result.NotAuthorized, useCase(params()))
    }

    @Test
    fun `missing record returns RecordNotFound`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { layawayRepository.getById("lay1") } returns null
        assertEquals(CancelLayawayUseCase.Result.RecordNotFound, useCase(params()))
    }

    @Test
    fun `cancelling a non-active layaway returns NotActive`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { layawayRepository.getById("lay1") } returns record(status = LayawayStatus.COMPLETED)
        assertEquals(CancelLayawayUseCase.Result.NotActive, useCase(params()))
    }

    @Test
    fun `cancel restores reserved stock and penalizes credit`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { layawayRepository.getById("lay1") } returns record(quantity = 2)
        assertEquals(CancelLayawayUseCase.Result.Success, useCase(params()))
        coVerify(exactly = 1) { layawayRepository.markCancelled("lay1", any()) }
        coVerify(exactly = 1) { productRepository.adjustQuantity("p1", 2) }
        coVerify(exactly = 1) { customerRepository.adjustCreditScore("c1", -20) }
    }
}
