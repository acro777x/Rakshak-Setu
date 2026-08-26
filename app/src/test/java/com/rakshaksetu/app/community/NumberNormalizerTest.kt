package com.rakshaksetu.app.community

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberNormalizerTest {

    @Test
    fun `indian formats collapse to national significant number`() {
        assertEquals("9876543210", NumberNormalizer.normalize("+91 98765 43210"))
        assertEquals("9876543210", NumberNormalizer.normalize("919876543210"))
        assertEquals("9876543210", NumberNormalizer.normalize("09876543210"))
        assertEquals("9876543210", NumberNormalizer.normalize("9876543210"))
        assertEquals("9876543210", NumberNormalizer.normalize("+91-98765-43210"))
    }

    @Test
    fun `international numbers keep full digits`() {
        assertEquals("447123456789", NumberNormalizer.normalize("+44 7123 456789"))
        assertEquals("923331234567", NumberNormalizer.normalize("+92 333 1234567"))
    }

    @Test
    fun `short codes preserved`() {
        assertEquals("1930", NumberNormalizer.normalize("1930"))
        assertEquals("100", NumberNormalizer.normalize("100"))
    }

    @Test
    fun `garbage input yields empty`() {
        assertEquals("", NumberNormalizer.normalize("no-digits-here"))
        assertEquals("", NumberNormalizer.normalize(""))
    }

    @Test
    fun `classification helpers`() {
        assertTrue(NumberNormalizer.isIndianNational("9876543210"))
        assertTrue(NumberNormalizer.isInternational("447123456789"))
        assertFalse(NumberNormalizer.isInternational("9876543210"))
    }
}
