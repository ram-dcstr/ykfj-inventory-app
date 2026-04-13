package com.ykfj.inventory.ui.auth

import com.ykfj.inventory.domain.model.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class IdleTimeout(val label: String, val millis: Long) {
    FIFTEEN_MIN("15 min", 15 * 60 * 1_000L),
    THIRTY_MIN("30 min", 30 * 60 * 1_000L),
    ONE_HOUR("1 hour", 60 * 60 * 1_000L),
    NEVER("Never", Long.MAX_VALUE),
}

@Singleton
class SessionManager @Inject constructor() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private var _idleTimeout: IdleTimeout = IdleTimeout.THIRTY_MIN
    val idleTimeout: IdleTimeout get() = _idleTimeout

    private var lastActivityTimestamp: Long = 0L

    val isLoggedIn: Boolean get() = _currentUser.value != null

    fun login(user: User) {
        _currentUser.value = user
        recordActivity()
    }

    fun logout() {
        _currentUser.value = null
        lastActivityTimestamp = 0L
    }

    fun setIdleTimeout(timeout: IdleTimeout) {
        _idleTimeout = timeout
    }

    /** Call on every user interaction to reset the idle timer. */
    fun recordActivity() {
        lastActivityTimestamp = System.currentTimeMillis()
    }

    /** Returns `true` if the session has been idle longer than the configured timeout. */
    fun isSessionExpired(): Boolean {
        if (_idleTimeout == IdleTimeout.NEVER) return false
        if (lastActivityTimestamp == 0L) return true
        return System.currentTimeMillis() - lastActivityTimestamp > _idleTimeout.millis
    }
}
