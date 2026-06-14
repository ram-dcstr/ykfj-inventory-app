package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Deleting a layaway payment is Admin-only (now enforced in the use case). */
class DeleteLayawayPaymentUseCaseTest {

    private lateinit var layawayRepository: LayawayRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: DeleteLayawayPaymentUseCase

    @Before
    fun setUp() {
        layawayRepository = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = DeleteLayawayPaymentUseCase(layawayRepository, userRepository, logActivity)
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    private fun params() =
        DeleteLayawayPaymentUseCase.Params(transactionId = "tx1", layawayId = "lay1", actorUserId = "actor")

    @Test
    fun `non-admin is not authorized and nothing is deleted`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.STAFF)
        assertEquals(DeleteLayawayPaymentUseCase.Result.NotAuthorized, useCase(params()))
        coVerify(exactly = 0) { layawayRepository.deletePayment(any()) }
    }

    @Test
    fun `admin can delete the payment`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        assertEquals(DeleteLayawayPaymentUseCase.Result.Success, useCase(params()))
        coVerify(exactly = 1) { layawayRepository.deletePayment("tx1") }
    }
}
