package com.ykfj.inventory.data.remote.sync

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-username failed-login throttle. Lives for the lifetime of the Ktor server.
 *
 * Without this, an attacker on the LAN can brute-force bcrypt-protected
 * passwords at line rate. We rate-limit by username (lowercased) rather than
 * by client IP because IP is shared/spoofable on a typical small-shop LAN and
 * the username is the smallest unit that should be protected.
 *
 * Lockout policy: [MAX_ATTEMPTS] consecutive failures locks the account for
 * [LOCKOUT_MS]. A successful login clears the counter. Locking out a username
 * that doesn't actually exist is fine — that's part of preventing username
 * enumeration via differential timing.
 *
 * In-memory only — lockouts reset when the tablet reboots / sync server
 * restarts. That's acceptable: it means we don't persist DOS state.
 */
class LoginThrottle {

    private data class State(
        @Volatile var failedCount: Int,
        @Volatile var lockedUntil: Long,
    )

    private val states = ConcurrentHashMap<String, State>()

    /**
     * Returns the number of seconds the caller should retry after, or `null` if
     * the username is currently allowed to attempt a login.
     */
    fun retryAfterSeconds(username: String, now: Long): Long? {
        val state = states[username.lowercase()] ?: return null
        return if (now < state.lockedUntil) {
            ((state.lockedUntil - now) + 999L) / 1000L
        } else {
            null
        }
    }

    /** Record a failed attempt; locks the account if it crosses [MAX_ATTEMPTS]. */
    fun recordFailure(username: String, now: Long) {
        val key = username.lowercase()
        states.compute(key) { _, existing ->
            val state = existing ?: State(failedCount = 0, lockedUntil = 0)
            // A prior lockout that's elapsed → reset the window before counting this one.
            if (state.lockedUntil in 1..now) {
                state.failedCount = 0
                state.lockedUntil = 0
            }
            state.failedCount += 1
            if (state.failedCount >= MAX_ATTEMPTS) {
                state.lockedUntil = now + LOCKOUT_MS
            }
            state
        }
    }

    /** Clear all throttling state for [username] after a successful login. */
    fun recordSuccess(username: String) {
        states.remove(username.lowercase())
    }

    companion object {
        /** Allow this many failed attempts in a row before locking. */
        const val MAX_ATTEMPTS = 5
        /** How long a lockout persists once triggered. */
        const val LOCKOUT_MS = 5L * 60L * 1000L
    }
}
