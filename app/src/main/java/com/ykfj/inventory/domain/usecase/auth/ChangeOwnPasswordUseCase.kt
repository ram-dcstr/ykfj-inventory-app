package com.ykfj.inventory.domain.usecase.auth

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

/**
 * Lets the signed-in user set a new password for their own account, clearing the
 * must-change-password flag. Used by the forced-change screen that gates a freshly
 * seeded admin out of the app until the bootstrap password is replaced.
 */
class ChangeOwnPasswordUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {
    sealed interface Result {
        data class Success(val user: User) : Result
        data object TooShort : Result
        data object SameAsCurrent : Result
        data object NotFound : Result
    }

    suspend operator fun invoke(userId: String, newPassword: String): Result {
        if (newPassword.length < MIN_PASSWORD_LENGTH) return Result.TooShort
        val user = userRepository.getById(userId) ?: return Result.NotFound
        // Block the no-op of re-setting the same bootstrap password we're forcing
        // the user away from — authenticate() succeeds only if it matches the
        // current stored hash.
        if (userRepository.authenticate(user.username, newPassword) != null) {
            return Result.SameAsCurrent
        }

        val updated = userRepository.changeOwnPassword(userId, newPassword)
            ?: return Result.NotFound

        logActivity(
            userId = userId,
            action = ActivityAction.UPDATE,
            description = "Changed own password",
            entityType = "user",
            entityId = userId,
        )
        return Result.Success(updated)
    }

    companion object {
        const val MIN_PASSWORD_LENGTH = 6
    }
}
