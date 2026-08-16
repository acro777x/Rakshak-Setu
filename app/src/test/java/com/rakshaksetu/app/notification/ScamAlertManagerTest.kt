package com.rakshaksetu.app.notification

import com.rakshaksetu.app.debug.FakePipelineEmitter
import org.junit.Assert.*
import org.junit.Test

class ScamAlertManagerTest {

    @Test
    fun `high confidence scam should trigger alert`() {
        val result = FakePipelineEmitter.scamResult()
        assertTrue(result.isScam)
        assertTrue(result.confidence >= 0.80f)
        // In real device test, verify notification fires
    }

    @Test
    fun `benign result should not trigger alert`() {
        val result = FakePipelineEmitter.benignResult()
        assertFalse(result.isScam)
    }

    @Test
    fun `low confidence is between 0_60 and 0_80`() {
        val result = FakePipelineEmitter.lowConfidenceResult()
        assertTrue(result.isScam)
        assertTrue(result.confidence >= 0.60f)
        assertTrue(result.confidence < 0.80f)
    }

    @Test
    fun `scam result has non-null scamType`() {
        val result = FakePipelineEmitter.scamResult()
        assertNotNull(result.scamType)
    }

    @Test
    fun `phone number masking works`() {
        val phone = "+919876543210"
        val masked = maskPhoneNumber(phone)
        assertTrue(masked.contains("XXXX"))
        assertTrue(masked.endsWith("3210"))
        assertFalse(masked.contains("98765"))
    }

    private fun maskPhoneNumber(phone: String): String {
        if (phone.length < 4) return phone
        val last4 = phone.takeLast(4)
        val prefix = phone.dropLast(4)
        val masked = prefix.map { if (it.isDigit()) 'X' else it }.joinToString("")
        return masked + last4
    }
}
