package com.rakshaksetu.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadAnalyzerTest {

    @Test
    fun `upi collect request extracts the payee handle`() {
        val a = QrPayloadAnalyzer.analyze(
            "upi://pay?pa=fraudster@okhdfcbank&pn=Victim&am=50000&tn=emergency"
        )
        assertEquals(QrPayloadAnalyzer.Kind.UPI_COLLECT, a.kind)
        assertEquals("fraudster@okhdfcbank", a.upiHandle)
        assertEquals("50000", a.amount)
        assertTrue(a.isActionable)
        assertTrue(a.warnings.isNotEmpty())
    }

    @Test
    fun `upi without amount warns that user picks the amount`() {
        val a = QrPayloadAnalyzer.analyze("upi://pay?pa=shop@ybl")
        assertNull(a.amount)
        assertTrue(a.warnings.any { it.contains("No amount") })
    }

    @Test
    fun `upi handle that is a phone number is called out`() {
        val a = QrPayloadAnalyzer.analyze("upi://pay?pa=9876543210&am=100")
        assertTrue(a.warnings.any { it.contains("plain phone number") })
    }

    @Test
    fun `plain http url payload is classified as url`() {
        val a = QrPayloadAnalyzer.analyze("http://free-gift.xyz/claim")
        assertEquals(QrPayloadAnalyzer.Kind.URL, a.kind)
        assertEquals("http://free-gift.xyz/claim", a.nestedUrl)
        assertTrue(a.warnings.any { it.contains("unencrypted") })
    }

    @Test
    fun `https url has no tls warning`() {
        val a = QrPayloadAnalyzer.analyze("https://sbi.co.in/")
        assertEquals(QrPayloadAnalyzer.Kind.URL, a.kind)
        assertTrue(a.warnings.none { it.contains("unencrypted") })
    }

    @Test
    fun `payment app deep link is detected`() {
        val a = QrPayloadAnalyzer.analyze("phonepe://pay?pa=x@ybl&pn=Test")
        assertEquals(QrPayloadAnalyzer.Kind.PAYMENT_DEEP_LINK, a.kind)
        assertTrue(a.isActionable)
    }

    @Test
    fun `wifi credential payload detected`() {
        val a = QrPayloadAnalyzer.analyze("WIFI:T:WPA;S:FreeHotspot;P:hunter2;;")
        assertEquals(QrPayloadAnalyzer.Kind.WIFI_CREDENTIALS, a.kind)
    }

    @Test
    fun `url embedded in text is extracted`() {
        val a = QrPayloadAnalyzer.analyze("Scan me at https://evil.example/xyz for more")
        assertEquals("https://evil.example/xyz", a.nestedUrl)
    }

    @Test
    fun `trailing punctuation is trimmed from extracted url`() {
        assertEquals("https://example.com/a", QrPayloadAnalyzer.extractUrl("https://example.com/a."))
    }

    @Test
    fun `tel payload is not treated as url`() {
        val a = QrPayloadAnalyzer.analyze("tel:+919876543210")
        assertEquals(QrPayloadAnalyzer.Kind.PLAIN_TEXT, a.kind)
        assertNull(a.nestedUrl)
    }

    @Test
    fun `empty payload is unknown`() {
        val a = QrPayloadAnalyzer.analyze("")
        assertEquals(QrPayloadAnalyzer.Kind.UNKNOWN, a.kind)
    }

    @Test
    fun `url encoded payee name is decoded`() {
        val a = QrPayloadAnalyzer.analyze("upi://pay?pa=a@b&pn=Ravi%20Kumar")
        assertEquals("Ravi Kumar", a.payeeName)
    }

    @Test
    fun `every analysis carries a label`() {
        listOf(
            "upi://pay?pa=a@b", "https://x.com", "WIFI:T:WPA;S:a;", "hello", "tel:123"
        ).forEach { assertNotNull(QrPayloadAnalyzer.analyze(it).label) }
    }
}
