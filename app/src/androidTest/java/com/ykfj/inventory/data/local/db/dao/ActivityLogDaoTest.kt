package com.ykfj.inventory.data.local.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.entity.ActivityLogEntity
import com.ykfj.inventory.data.local.db.enums.ActivityAction
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityLogDaoTest {

    private lateinit var db: YkfjDatabase
    private lateinit var dao: ActivityLogDao

    private fun log(
        id: String,
        userId: String = "u1",
        action: ActivityAction = ActivityAction.LOGIN,
        entityType: String? = null,
        timestamp: Long = 1_000L,
    ) = ActivityLogEntity(
        log_id = id,
        user_id = userId,
        action = action,
        entity_type = entityType,
        entity_id = null,
        description = "test",
        timestamp = timestamp,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, YkfjDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.activityLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun observeFiltered_emitsAllWhenNoFilters() = runTest {
        dao.observeFiltered(null, null, null, null, null).test {
            assertEquals(emptyList<ActivityLogEntity>(), awaitItem())

            dao.insert(log("l1"))
            val first = awaitItem()
            assertEquals(1, first.size)

            dao.insert(log("l2", timestamp = 2_000L))
            val second = awaitItem()
            assertEquals(2, second.size)
            // ordered by timestamp DESC
            assertEquals("l2", second[0].log_id)
            assertEquals("l1", second[1].log_id)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeFiltered_filtersByUserId() = runTest {
        dao.insert(log("l1", userId = "u1"))
        dao.insert(log("l2", userId = "u2"))

        dao.observeFiltered("u1", null, null, null, null).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("u1", result[0].user_id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeFiltered_filtersByAction() = runTest {
        dao.insert(log("l1", action = ActivityAction.LOGIN))
        dao.insert(log("l2", action = ActivityAction.UPDATE))

        dao.observeFiltered(null, ActivityAction.UPDATE, null, null, null).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals(ActivityAction.UPDATE, result[0].action)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeFiltered_filtersByEntityTypeAndTimeRange() = runTest {
        dao.insert(log("l1", entityType = "product", timestamp = 1_000L))
        dao.insert(log("l2", entityType = "user", timestamp = 2_000L))
        dao.insert(log("l3", entityType = "product", timestamp = 5_000L))

        dao.observeFiltered(null, null, "product", 2_000L, 10_000L).test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("l3", result[0].log_id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteOlderThan_removesExpiredRows() = runTest {
        dao.insert(log("old", timestamp = 1_000L))
        dao.insert(log("recent", timestamp = 10_000L))

        val deleted = dao.deleteOlderThan(5_000L)
        assertEquals(1, deleted)

        dao.observeFiltered(null, null, null, null, null).test {
            val remaining = awaitItem()
            assertEquals(1, remaining.size)
            assertEquals("recent", remaining[0].log_id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getForExport_returnsAscendingInRange() = runTest {
        dao.insert(log("l1", timestamp = 3_000L))
        dao.insert(log("l2", timestamp = 1_000L))
        dao.insert(log("l3", timestamp = 2_000L))

        val result = dao.getForExport(null, 1_000L, 3_000L)
        assertEquals(listOf("l2", "l3", "l1"), result.map { it.log_id })
    }
}
