package com.rakshaksetu.app.action

import org.junit.Assert.*
import org.junit.Test

class BankEmailActionTest {

    @Test
    fun `getBanks returns 20 entries`() {
        assertEquals(20, BankEmailAction.getBanks().size)
    }

    @Test
    fun `all banks have non-blank fraud email`() {
        BankEmailAction.getBanks().forEach { bank ->
            assertTrue("${bank.name} has blank email", bank.fraudEmail.isNotBlank())
            assertTrue("${bank.name} email has no @", bank.fraudEmail.contains("@"))
        }
    }

    @Test
    fun `findBankByName SBI returns SBI`() {
        val bank = BankEmailAction.findBankByName("SBI")
        assertNotNull(bank)
        assertEquals("SBI", bank!!.name)
    }

    @Test
    fun `findBankByName is case insensitive`() {
        val bank = BankEmailAction.findBankByName("sbi")
        assertNotNull(bank)
        assertEquals("SBI", bank!!.name)
    }

    @Test
    fun `findBankByName returns null for unknown`() {
        assertNull(BankEmailAction.findBankByName("nonexistent_bank"))
    }
}
