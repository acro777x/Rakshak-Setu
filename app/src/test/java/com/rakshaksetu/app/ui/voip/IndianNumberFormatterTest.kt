package com.rakshaksetu.app.ui.voip

import org.junit.Assert.assertEquals
import org.junit.Test

class IndianNumberFormatterTest {

    @Test
    fun testTenDigitMobileFormattedWithPlus91() {
        val input = "9876543210"
        val formatted = IndianNumberFormatter.format(input)
        assertEquals("+91 98765 43210", formatted)
    }

    @Test
    fun testRawWithPlus91FormattedNicely() {
        val input = "+919876500001"
        val formatted = IndianNumberFormatter.format(input)
        assertEquals("+91 98765 00001", formatted)
    }

    @Test
    fun testPartialDigits() {
        assertEquals("98765", IndianNumberFormatter.format("98765"))
        assertEquals("98765 4", IndianNumberFormatter.format("987654"))
        assertEquals("98765 432", IndianNumberFormatter.format("98765432"))
    }

    @Test
    fun testEmptyInput() {
        assertEquals("", IndianNumberFormatter.format(""))
        assertEquals("", IndianNumberFormatter.format("   "))
    }

    @Test
    fun testToDialableConversion() {
        assertEquals("+919876543210", IndianNumberFormatter.toDialable("9876543210"))
        assertEquals("+919876543210", IndianNumberFormatter.toDialable("+91 98765 43210"))
        assertEquals("+919876543210", IndianNumberFormatter.toDialable("+919876543210"))
        assertEquals("1930", IndianNumberFormatter.toDialable("1930"))
    }

    @Test
    fun testNonNumericHandling() {
        val formatted = IndianNumberFormatter.format("DCP Cyber Crime")
        assertEquals("DCP Cyber Crime", formatted)
    }
}
