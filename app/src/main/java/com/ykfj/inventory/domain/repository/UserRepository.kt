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

    /** Batch lookup — one query for many ids, used to enrich lists without N+1. */
    suspend fun getByIds(ids: List<String>): List<User>

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

    /**
     * Sets a new password for [userId] **and** clears the must-change-password
     * flag — used by the self-service forced-change flow. Returns the updated
     * [User] (reflecting the cleared flag) or `null` if the user is gone.
     */
    suspend fun changeOwnPassword(userId: String, newPlaintext: String): User?

    /** Soft delete — flips `is_deleted` and `is_active`. */
    suspend fun deactivate(userId: String)
}
