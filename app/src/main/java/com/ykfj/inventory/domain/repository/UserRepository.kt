package com.ykfj.inventory.domain.repository

import com.ykfj.inventory.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * Auth + user directory. Password hashing and verification live inside the
 * implementation — use cases pass plaintext passwords and the repository
 * never exposes the stored hash.
 */
interface UserRepository {

    fun observeActiveUsers(): Flow<List<User>>

    suspend fun getById(id: String): User?

    suspend fun getByUsername(username: String): User?

    /**
     * Verifies [plaintextPassword] against the stored bcrypt hash for
     * [username]. Returns the [User] on success or `null` on any failure
     * (unknown username, inactive account, wrong password).
     */
    suspend fun authenticate(username: String, plaintextPassword: String): User?

    /** Create a new user with the given plaintext password (hashed inside). */
    suspend fun create(user: User, plaintextPassword: String)

    suspend fun update(user: User)

    /** Replaces the stored hash with a fresh one derived from [newPlaintext]. */
    suspend fun resetPassword(userId: String, newPlaintext: String)

    /** Soft delete — flips `is_deleted` and `is_active`. */
    suspend fun deactivate(userId: String)
}
