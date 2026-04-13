package com.ykfj.inventory.domain.usecase.auth

import com.ykfj.inventory.data.local.db.enums.ActivityAction
import com.ykfj.inventory.domain.model.User
import com.ykfj.inventory.domain.repository.UserRepository
import com.ykfj.inventory.domain.usecase.activitylog.LogActivityUseCase
import javax.inject.Inject

sealed interface LoginResult {
    data class Success(val user: User) : LoginResult
    data object InvalidCredentials : LoginResult
}

class LoginUseCase @Inject constructor(
    private val userRepository: UserRepository,
    private val logActivity: LogActivityUseCase,
) {

    suspend operator fun invoke(username: String, password: String): LoginResult {
        val user = userRepository.authenticate(username, password)
            ?: return LoginResult.InvalidCredentials

        logActivity(
            userId = user.id,
            action = ActivityAction.LOGIN,
            description = "User '${user.username}' logged in",
        )

        return LoginResult.Success(user)
    }
}
