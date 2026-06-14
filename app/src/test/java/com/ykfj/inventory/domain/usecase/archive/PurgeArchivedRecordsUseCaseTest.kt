package com.ykfj.inventory.domain.usecase.archive

import com.ykfj.inventory.data.local.db.dao.DamagedRecordDao
import com.ykfj.inventory.data.local.db.dao.LayawayRecordDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganGroupDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganPaymentDao
import com.ykfj.inventory.data.local.db.dao.PaluwaganSlotDao
import com.ykfj.inventory.data.local.db.dao.SoldRecordDao
import com.ykfj.inventory.data.local.db.dao.UserDao
import com.ykfj.inventory.data.local.db.entity.UserEntity
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Purging archives is a destructive hard-delete; Admin-only (now enforced). */
class PurgeArchivedRecordsUseCaseTest {

    private lateinit var soldDao: SoldRecordDao
    private lateinit var layawayDao: LayawayRecordDao
    private lateinit var damagedDao: DamagedRecordDao
    private lateinit var groupDao: PaluwaganGroupDao
    private lateinit var slotDao: PaluwaganSlotDao
    private lateinit var paymentDao: PaluwaganPaymentDao
    private lateinit var userDao: UserDao
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: PurgeArchivedRecordsUseCase

    @Before
    fun setUp() {
        soldDao = mockk(relaxed = true)
        layawayDao = mockk(relaxed = true)
        damagedDao = mockk(relaxed = true)
        groupDao = mockk(relaxed = true)
        slotDao = mockk(relaxed = true)
        paymentDao = mockk(relaxed = true)
        userDao = mockk(relaxed = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = PurgeArchivedRecordsUseCase(
            soldDao, layawayDao, damagedDao, groupDao, slotDao, paymentDao, userDao, logActivity,
        )
    }

    private fun userEntity(role: UserRole): UserEntity {
        val u = mockk<UserEntity>(relaxed = true)
        every { u.role } returns role
        return u
    }

    @Test
    fun `non-admin is not authorized and deletes nothing`() = runTest {
        coEvery { userDao.getById("actor") } returns userEntity(UserRole.MANAGER)
        assertEquals(
            PurgeArchivedRecordsUseCase.Result.NotAuthorized,
            useCase(ArchivableRecordType.SOLD, 0, 1, "actor"),
        )
        coVerify(exactly = 0) { soldDao.hardDeleteArchivedInRange(any(), any()) }
    }

    @Test
    fun `admin purge returns the deleted count`() = runTest {
        coEvery { userDao.getById("actor") } returns userEntity(UserRole.ADMIN)
        coEvery { soldDao.hardDeleteArchivedInRange(0, 1) } returns 3
        assertEquals(
            PurgeArchivedRecordsUseCase.Result.Success(3),
            useCase(ArchivableRecordType.SOLD, 0, 1, "actor"),
        )
    }
}
