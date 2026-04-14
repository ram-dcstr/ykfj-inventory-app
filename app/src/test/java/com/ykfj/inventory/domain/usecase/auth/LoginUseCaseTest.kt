package com.ykfj.inventory.domain.usecase.auth

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LoginUseCaseTest {

    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var loginUseCase: LoginUseCase

    private val adminUser = User(
        id = "user-1",
        username = "admin",
        name = "Admin User",
        role = UserRole.ADMIN,
        isActive = true,
        createdAt = 1_000_000L,
        updatedAt = 1_000_000L,
    )

    @Before
    fun setUp() {
        userRepository = mockk()
        logActivity = mockk(relaxUnitFun = true)
        loginUseCase = LoginUseCase(userRepository, logActivity)
    }

    @Test
    fun `returns Success and logs activity on valid credentials`() = runTest {
        coEvery { userRepository.authenticate("admin", "admin123") } returns adminUser

        val result = loginUseCase("admin", "admin123")

        assertTrue(result is LoginResult.Success)
        assertEquals(adminUser, (result as LoginResult.Success).user)

        coVerify {
            logActivity(
                userId = "user-1",
                action = ActivityAction.LOGIN,
                description = "User 'admin' logged in",
            )
        }
    }

    @Test
    fun `returns InvalidCredentials when authenticate returns null`() = runTest {
        coEvery { userRepository.authenticate("admin", "wrong") } returns null

        val result = loginUseCase("admin", "wrong")

        assertTrue(result is LoginResult.InvalidCredentials)
        coVerify(exactly = 0) { logActivity(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `returns InvalidCredentials for unknown username`() = runTest {
        coEvery { userRepository.authenticate("unknown", "pass") } returns null

        val result = loginUseCase("unknown", "pass")

        assertTrue(result is LoginResult.InvalidCredentials)
    }
}
