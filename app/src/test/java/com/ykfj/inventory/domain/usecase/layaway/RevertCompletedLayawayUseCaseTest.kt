package com.ykfj.inventory.domain.usecase.layaway

import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.ProductRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Reverting a completed layaway is Admin-only (now enforced in the use case). */
class RevertCompletedLayawayUseCaseTest {

    private lateinit var layawayRepository: LayawayRepository
    private lateinit var soldRecordRepository: SoldRecordRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: RevertCompletedLayawayUseCase

    @Before
    fun setUp() {
        layawayRepository = mockk(relaxUnitFun = true)
        soldRecordRepository = mockk(relaxUnitFun = true)
        productRepository = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = RevertCompletedLayawayUseCase(
            layawayRepository, soldRecordRepository, productRepository, userRepository, logActivity,
        )
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    private fun params() = RevertCompletedLayawayUseCase.Params(layawayId = "lay1", actorUserId = "actor")

    @Test
    fun `non-admin is not authorized`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.MANAGER)
        assertEquals(RevertCompletedLayawayUseCase.Result.NotAuthorized, useCase(params()))
    }

    @Test
    fun `admin passes the role gate`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.ADMIN)
        coEvery { layawayRepository.getById("lay1") } returns null
        assertEquals(RevertCompletedLayawayUseCase.Result.RecordNotFound, useCase(params()))
    }
}
