package com.ykfj.inventory.util

import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordHasher {

    private const val COST = 12

    /** Returns a bcrypt hash string for the given [plaintext] password. */
    fun hash(plaintext: String): String =
        BCrypt.withDefaults().hashToString(COST, plaintext.toCharArray())

    /** Verifies [plaintext] against a bcrypt [hash]. */
    fun verify(plaintext: String, hash: String): Boolean =
        BCrypt.verifyer().verify(plaintext.toCharArray(), hash).verified
}
