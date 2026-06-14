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
    NEVER("Never", Long.MAX_VALUE);

    companion object {
        /** Lenient `valueOf` for round-tripping the persisted enum name. */
        fun fromName(name: String?): IdleTimeout =
            entries.firstOrNull { it.name == name } ?: THIRTY_MIN
    }
}

@Singleton
class SessionManager @Inject constructor() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _idleTimeout = MutableStateFlow(IdleTimeout.THIRTY_MIN)
    val idleTimeoutFlow: StateFlow<IdleTimeout> = _idleTimeout.asStateFlow()
    val idleTimeout: IdleTimeout get() = _idleTimeout.value

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

    /**
     * Replace the signed-in user in place (same session) — e.g. after a forced
     * password change clears `mustChangePassword`. No-op if logged out.
     */
    fun updateCurrentUser(user: User) {
        if (_currentUser.value?.id == user.id) _currentUser.value = user
    }

    fun setIdleTimeout(timeout: IdleTimeout) {
        _idleTimeout.value = timeout
    }

    /** Call on every user interaction to reset the idle timer. */
    fun recordActivity() {
        lastActivityTimestamp = System.currentTimeMillis()
    }

    /** Returns `true` if the session has been idle longer than the configured timeout. */
    fun isSessionExpired(): Boolean {
        if (_idleTimeout.value == IdleTimeout.NEVER) return false
        if (lastActivityTimestamp == 0L) return true
        return System.currentTimeMillis() - lastActivityTimestamp > _idleTimeout.value.millis
    }

    // ── App lock: require re-login after the app has been in the background a while ──

    private var backgroundedAt: Long = 0L

    /** Call when the whole app goes to the background (e.g. Activity `onStop`). */
    fun onAppBackgrounded() {
        backgroundedAt = System.currentTimeMillis()
    }

    /**
     * Call when the app returns to the foreground (e.g. Activity `onStart`). Logs out and
     * returns `true` if the app was backgrounded longer than [BACKGROUND_LOCK_GRACE_MS] —
     * long enough that the user actually left, as opposed to a quick hop out to the photo
     * picker / share sheet / file picker. Returning within the grace keeps the session.
     */
    fun onAppForegrounded(): Boolean {
        val backgroundedSince = backgroundedAt
        backgroundedAt = 0L
        if (!isLoggedIn || backgroundedSince == 0L) return false
        if (System.currentTimeMillis() - backgroundedSince > BACKGROUND_LOCK_GRACE_MS) {
            logout()
            return true
        }
        return false
    }

    companion object {
        /**
         * Grace window for app backgrounding. Coming back within this stays logged in (so
         * the image picker / share sheet / file picker don't kick you out mid-task); staying
         * away longer requires logging in again. A full app restart always requires login,
         * since the session is held only in memory.
         */
        private const val BACKGROUND_LOCK_GRACE_MS = 60_000L
    }
}
