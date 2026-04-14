package com.ykfj.inventory.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun `hash returns bcrypt string`() {
        val hash = PasswordHasher.hash("secret123")
        assertTrue(hash.startsWith("\$2a\$12\$") || hash.startsWith("\$2y\$12\$"))
    }

    @Test
    fun `verify returns true for correct password`() {
        val hash = PasswordHasher.hash("myPassword")
        assertTrue(PasswordHasher.verify("myPassword", hash))
    }

    @Test
    fun `verify returns false for wrong password`() {
        val hash = PasswordHasher.hash("myPassword")
        assertFalse(PasswordHasher.verify("wrongPassword", hash))
    }

    @Test
    fun `hash produces different outputs for same input`() {
        val hash1 = PasswordHasher.hash("sameInput")
        val hash2 = PasswordHasher.hash("sameInput")
        assertNotEquals(hash1, hash2)
        assertTrue(PasswordHasher.verify("sameInput", hash1))
        assertTrue(PasswordHasher.verify("sameInput", hash2))
    }

    @Test
    fun `verify handles empty password`() {
        val hash = PasswordHasher.hash("")
        assertTrue(PasswordHasher.verify("", hash))
        assertFalse(PasswordHasher.verify("notEmpty", hash))
    }
}
