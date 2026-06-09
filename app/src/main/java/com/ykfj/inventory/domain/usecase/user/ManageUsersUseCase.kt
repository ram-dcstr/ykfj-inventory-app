package com.ykfj.inventory.domain.usecase.user

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.data.local.db.enums.UserRole
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import java.util.UUID
import javax.inject.Inject

/**
 * Admin-only operations for managing user accounts. Each operation returns a
 * [Result] so the ViewModel can surface field-level errors without exceptions.
 *
 * Sync caveat: only metadata edits and deactivations propagate to the tablet.
 * Brand-new users and password resets are stored locally; the server's
 * `/api/sync/push` handler skips inserts and never accepts `password_hash`.
 * Practically, password resets and account creation should happen on the tablet.
 */
class ManageUsersUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data class Success(val user: User) : Result
        data object UsernameTaken : Result
        data object InvalidUsername : Result
        data object InvalidName : Result
        data object InvalidPassword : Result
        data object NotFound : Result
        data object CannotDeactivateSelf : Result
    }

    suspend fun create(
        username: String,
        name: String,
        plaintextPassword: String,
        role: UserRole,
        actorUserId: String,
    ): Result {
        val u = username.trim().lowercase()
        val n = name.trim()
        if (u.isBlank() || u.length < 3) return Result.InvalidUsername
        if (n.isBlank()) return Result.InvalidName
        if (plaintextPassword.length < MIN_PASSWORD_LENGTH) return Result.InvalidPassword
        if (userRepository.getByUsername(u) != null) return Result.UsernameTaken

        val now = System.currentTimeMillis()
        val user = User(
            id = UUID.randomUUID().toString(),
            username = u,
            name = n,
            role = role,
            isActive = true,
            createdAt = now,
            updatedAt = now,
        )
        userRepository.create(user, plaintextPassword)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.CREATE,
            description = "Created user '$u' (${role.name})",
            entityType = "user",
            entityId = user.id,
        )
        return Result.Success(user)
    }

    suspend fun update(
        userId: String,
        username: String,
        name: String,
        role: UserRole,
        actorUserId: String,
    ): Result {
        val existing = userRepository.getById(userId) ?: return Result.NotFound
        val u = username.trim().lowercase()
        val n = name.trim()
        if (u.isBlank() || u.length < 3) return Result.InvalidUsername
        if (n.isBlank()) return Result.InvalidName
        if (u != existing.username) {
            val conflict = userRepository.getByUsername(u)
            if (conflict != null && conflict.id != userId) return Result.UsernameTaken
        }

        val updated = existing.copy(
            username = u,
            name = n,
            role = role,
            updatedAt = System.currentTimeMillis(),
        )
        userRepository.update(updated)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.UPDATE,
            description = "Updated user '${existing.username}' → '$u' (${role.name})",
            entityType = "user",
            entityId = userId,
            oldValue = "${existing.username}:${existing.role.name}",
            newValue = "$u:${role.name}",
        )
        return Result.Success(updated)
    }

    suspend fun resetPassword(
        userId: String,
        newPlaintextPassword: String,
        actorUserId: String,
    ): Result {
        val existing = userRepository.getById(userId) ?: return Result.NotFound
        if (newPlaintextPassword.length < MIN_PASSWORD_LENGTH) return Result.InvalidPassword

        userRepository.resetPassword(userId, newPlaintextPassword)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.UPDATE,
            description = "Reset password for '${existing.username}'",
            entityType = "user",
            entityId = userId,
        )
        return Result.Success(existing)
    }

    suspend fun deactivate(userId: String, actorUserId: String): Result {
        if (userId == actorUserId) return Result.CannotDeactivateSelf
        val existing = userRepository.getById(userId) ?: return Result.NotFound

        userRepository.deactivate(userId)
        logActivity(
            userId = actorUserId,
            action = ActivityAction.DELETE,
            description = "Deactivated user '${existing.username}'",
            entityType = "user",
            entityId = userId,
        )
        return Result.Success(existing)
    }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}
