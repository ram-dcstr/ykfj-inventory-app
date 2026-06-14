package com.ykfj.inventory.domain.usecase.archive

import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.DamagedRecordRepository
import com.ykfj.inventory.domain.repository.LayawayRepository
import com.ykfj.inventory.domain.repository.PaluwaganRepository
import com.ykfj.inventory.domain.repository.SoldRecordRepository
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Archiving a record is Admin/Manager-only (now enforced in the use case). */
class ArchiveRecordUseCaseTest {

    private lateinit var soldRepo: SoldRecordRepository
    private lateinit var layawayRepo: LayawayRepository
    private lateinit var damagedRepo: DamagedRecordRepository
    private lateinit var paluwaganRepo: PaluwaganRepository
    private lateinit var userRepository: UserRepository
    private lateinit var logActivity: LogActivityUseCase
    private lateinit var useCase: ArchiveRecordUseCase

    @Before
    fun setUp() {
        soldRepo = mockk(relaxUnitFun = true)
        layawayRepo = mockk(relaxUnitFun = true)
        damagedRepo = mockk(relaxUnitFun = true)
        paluwaganRepo = mockk(relaxUnitFun = true)
        userRepository = mockk(relaxUnitFun = true)
        logActivity = mockk(relaxUnitFun = true)
        useCase = ArchiveRecordUseCase(
            soldRepo, layawayRepo, damagedRepo, paluwaganRepo, userRepository, logActivity,
        )
    }

    private fun user(role: UserRole) = User(
        id = "actor", username = "u", name = "U", role = role,
        isActive = true, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `staff is not authorized and archives nothing`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.STAFF)
        assertEquals(
            ArchiveRecordUseCase.Result.NotAuthorized,
            useCase(ArchivableRecordType.SOLD, "rec1", "actor"),
        )
        coVerify(exactly = 0) { soldRepo.archive(any()) }
    }

    @Test
    fun `manager can archive a record`() = runTest {
        coEvery { userRepository.getById("actor") } returns user(UserRole.MANAGER)
        assertEquals(
            ArchiveRecordUseCase.Result.Success,
            useCase(ArchivableRecordType.SOLD, "rec1", "actor"),
        )
        coVerify(exactly = 1) { soldRepo.archive("rec1") }
    }
}
