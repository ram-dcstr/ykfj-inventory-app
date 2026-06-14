package com.ykfj.inventory.domain.usecase.activitylog

import android.content.Context
import com.ykfj.inventory.data.local.db.dao.ActivityLogDao
import com.ykfj.inventory.data.local.db.dao.UserDao
import com.ykfj.inventory.data.local.db.entity.UserEntity
import com.ykfj.inventory.data.local.db.enums.UserRole
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Activity-log CSV export is Admin-only (now enforced in the use case). */
class ExportActivityLogUseCaseTest {

    private lateinit var context: Context
    private lateinit var activityLogDao: ActivityLogDao
    private lateinit var userDao: UserDao
    private lateinit var useCase: ExportActivityLogUseCase

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        activityLogDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        useCase = ExportActivityLogUseCase(context, activityLogDao, userDao)
    }

    private fun userEntity(role: UserRole): UserEntity {
        val u = mockk<UserEntity>(relaxed = true)
        every { u.role } returns role
        return u
    }

    @Test
    fun `non-admin is not authorized and reads no rows`() = runTest {
        coEvery { userDao.getById("actor") } returns userEntity(UserRole.MANAGER)
        assertEquals(
            ExportActivityLogUseCase.Result.NotAuthorized,
            useCase(startMillis = 0, endMillis = 1, actorUserId = "actor"),
        )
        coVerify(exactly = 0) { activityLogDao.getForExport(any(), any(), any()) }
    }

    @Test
    fun `admin passes the role gate`() = runTest {
        coEvery { userDao.getById("actor") } returns userEntity(UserRole.ADMIN)
        coEvery { activityLogDao.getForExport(any(), any(), any()) } returns emptyList()
        assertEquals(
            ExportActivityLogUseCase.Result.NoRecords,
            useCase(startMillis = 0, endMillis = 1, actorUserId = "actor"),
        )
    }
}
