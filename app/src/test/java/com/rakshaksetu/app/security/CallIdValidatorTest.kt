package com.rakshaksetu.app.security

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CallIdValidatorTest {

    @Test
    fun `valid UUID passes`() {
        assertTrue(CallIdValidator.isValid(UUID.randomUUID().toString()))
    }

    @Test
    fun `path traversal rejected`() {
        assertFalse(CallIdValidator.isValid("../../etc/passwd"))
    }

    @Test
    fun `empty string rejected`() {
        assertFalse(CallIdValidator.isValid(""))
    }

    @Test
    fun `random string rejected`() {
        assertFalse(CallIdValidator.isValid("not-a-uuid-at-all"))
    }

    @Test
    fun `requireValid throws on invalid`() {
        try {
            CallIdValidator.requireValid("../hack")
            fail("Should have thrown")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `requireValid returns callId on valid`() {
        val uuid = UUID.randomUUID().toString()
        assertEquals(uuid, CallIdValidator.requireValid(uuid))
    }
}
