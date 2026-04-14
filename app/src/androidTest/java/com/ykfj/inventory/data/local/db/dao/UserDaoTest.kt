package com.ykfj.inventory.data.local.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ykfj.inventory.data.local.db.YkfjDatabase
import com.ykfj.inventory.data.local.db.entity.UserEntity
import com.ykfj.inventory.data.local.db.enums.UserRole
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UserDaoTest {

    private lateinit var db: YkfjDatabase
    private lateinit var userDao: UserDao

    private val now = System.currentTimeMillis()

    private fun user(
        id: String = "u1",
        username: String = "admin",
        role: UserRole = UserRole.ADMIN,
        isActive: Boolean = true,
        isDeleted: Boolean = false,
    ) = UserEntity(
        user_id = id,
        username = username,
        password_hash = "\$2a\$12\$fakehash",
        name = "Test User",
        role = role,
        is_active = isActive,
        created_at = now,
        updated_at = now,
        is_deleted = isDeleted,
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, YkfjDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        userDao = db.userDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertAndGetById() = runTest {
        val entity = user()
        userDao.insert(entity)
        val result = userDao.getById("u1")
        assertNotNull(result)
        assertEquals("admin", result!!.username)
        assertEquals(UserRole.ADMIN, result.role)
    }

    @Test
    fun getByUsername() = runTest {
        userDao.insert(user())
        val result = userDao.getByUsername("admin")
        assertNotNull(result)
        assertEquals("u1", result!!.user_id)
    }

    @Test
    fun getByUsername_returnsNullForDeleted() = runTest {
        userDao.insert(user(isDeleted = true))
        val result = userDao.getByUsername("admin")
        assertNull(result)
    }

    @Test
    fun observeAll_emitsUpdates() = runTest {
        userDao.observeAll().test {
            assertEquals(emptyList<UserEntity>(), awaitItem())

            userDao.insert(user(id = "u1", username = "alice"))
            val first = awaitItem()
            assertEquals(1, first.size)

            userDao.insert(user(id = "u2", username = "bob"))
            val second = awaitItem()
            assertEquals(2, second.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun observeAll_excludesDeletedUsers() = runTest {
        userDao.insert(user(id = "u1", username = "active"))
        userDao.insert(user(id = "u2", username = "deleted", isDeleted = true))

        userDao.observeAll().test {
            val users = awaitItem()
            assertEquals(1, users.size)
            assertEquals("active", users[0].username)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun count_excludesDeleted() = runTest {
        userDao.insert(user(id = "u1", username = "a"))
        userDao.insert(user(id = "u2", username = "b"))
        userDao.insert(user(id = "u3", username = "c", isDeleted = true))
        assertEquals(2, userDao.count())
    }

    @Test
    fun softDelete() = runTest {
        userDao.insert(user())
        userDao.softDelete("u1", System.currentTimeMillis())
        assertNull(userDao.getById("u1"))
    }

    @Test
    fun update() = runTest {
        userDao.insert(user())
        val updated = user().copy(name = "Updated Name", updated_at = now + 1000)
        userDao.update(updated)
        assertEquals("Updated Name", userDao.getById("u1")!!.name)
    }

    @Test
    fun uniqueUsernameIndex_enforced() = runTest {
        userDao.insert(user(id = "u1", username = "admin"))
        try {
            userDao.insert(user(id = "u2", username = "admin"))
            throw AssertionError("Expected unique constraint violation")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // expected
        }
    }
}
