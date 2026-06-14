package com.ykfj.inventory.data.repository

import com.ykfj.inventory.data.local.db.dao.UserDao
import com.ykfj.inventory.data.mapper.toDomain
import com.ykfj.inventory.data.mapper.toEntity
import com.ykfj.inventory.data.remote.sync.SyncEnqueuer
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.sync.SyncAction
import com.ykfj.inventory.util.PasswordHasher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val userDao: UserDao,
    private val syncEnqueuer: SyncEnqueuer,
) : UserRepository {

    override fun observeActiveUsers(): Flow<List<User>> =
        userDao.observeAll().map { list -> list.filter { it.is_active }.map { it.toDomain() } }

    override suspend fun getById(id: String): User? =
        userDao.getById(id)?.toDomain()

    override suspend fun getByUsername(username: String): User? =
        userDao.getByUsername(username)?.toDomain()

    override suspend fun authenticate(username: String, plaintextPassword: String): User? {
        val entity = userDao.getByUsername(username) ?: return null
        if (!entity.is_active) return null
        if (!PasswordHasher.verify(plaintextPassword, entity.password_hash)) return null
        return entity.toDomain()
    }

    override suspend fun create(user: User, plaintextPassword: String) {
        val hash = PasswordHasher.hash(plaintextPassword)
        val entity = user.toEntity(hash)
        userDao.insert(entity)
        syncEnqueuer.enqueueUser(entity, SyncAction.INSERT)
    }

    override suspend fun update(user: User) {
        val existing = userDao.getById(user.id) ?: return
        val entity = user.toEntity(existing.password_hash)
        userDao.update(entity)
        syncEnqueuer.enqueueUser(entity, SyncAction.UPDATE)
    }

    override suspend fun resetPassword(userId: String, newPlaintext: String) {
        val existing = userDao.getById(userId) ?: return
        val hash = PasswordHasher.hash(newPlaintext)
        val updated = existing.copy(password_hash = hash, updated_at = System.currentTimeMillis())
        userDao.update(updated)
        syncEnqueuer.enqueueUser(updated, SyncAction.UPDATE)
    }

    override suspend fun changeOwnPassword(userId: String, newPlaintext: String): User? {
        val existing = userDao.getById(userId) ?: return null
        val hash = PasswordHasher.hash(newPlaintext)
        val updated = existing.copy(
            password_hash = hash,
            must_change_password = false,
            updated_at = System.currentTimeMillis(),
        )
        userDao.update(updated)
        syncEnqueuer.enqueueUser(updated, SyncAction.UPDATE)
        return updated.toDomain()
    }

    override suspend fun deactivate(userId: String) {
        val existing = userDao.getById(userId) ?: return
        val updated = existing.copy(
            is_active = false,
            is_deleted = true,
            updated_at = System.currentTimeMillis(),
        )
        userDao.update(updated)
        syncEnqueuer.enqueueUser(updated, SyncAction.DELETE)
    }
}
