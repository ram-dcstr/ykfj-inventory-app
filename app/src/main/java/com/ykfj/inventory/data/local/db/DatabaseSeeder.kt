package com.ykfj.inventory.data.local.db

import at.favre.lib.crypto.bcrypt.BCrypt
import com.ykfj.inventory.data.local.db.entity.UserEntity
import com.ykfj.inventory.data.local.db.enums.UserRole
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds the database on first launch.
 *
 * Currently creates a default admin account (username: `admin`, password:
 * `admin123`). The admin is expected to rotate this password from Settings
 * immediately after the first login — see the Phase 1.6 checklist.
 *
 * Idempotent: running more than once is a no-op because it checks the
 * current user count before inserting.
 */
@Singleton
class DatabaseSeeder @Inject constructor(
    private val database: YkfjDatabase,
) {

    suspend fun seedIfEmpty() {
        val userDao = database.userDao()
        if (userDao.count() > 0) return

        val now = System.currentTimeMillis()
        val passwordHash = BCrypt.withDefaults()
            .hashToString(BCRYPT_COST, DEFAULT_ADMIN_PASSWORD.toCharArray())

        userDao.insert(
            UserEntity(
                user_id = UUID.randomUUID().toString(),
                username = DEFAULT_ADMIN_USERNAME,
                password_hash = passwordHash,
                name = "Administrator",
                role = UserRole.ADMIN,
                is_active = true,
                created_at = now,
                updated_at = now,
                is_deleted = false,
            ),
        )
    }

    private companion object {
        const val DEFAULT_ADMIN_USERNAME = "admin"
        const val DEFAULT_ADMIN_PASSWORD = "admin123"
        const val BCRYPT_COST = 12
    }
}
