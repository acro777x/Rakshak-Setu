package com.rakshaksetu.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the real URL engine.
 *
 * These pin down two classes of bug the old substring matcher had:
 *  - FALSE POSITIVES: a legitimate URL must not be flagged.
 *  - FALSE NEGATIVES: an obvious phishing URL must not pass.
 * A keyword scanner fails both; a layered engine must not.
 */
class UrlThreatHeuristicsTest {

    // ── FALSE NEGATIVES: these must be caught ───────────────────────────────
    @Test
    fun `sbi kyc lure on wrong domain is dangerous`() {
        val v = UrlThreatHeuristics.analyze("https://sbi-kyc-update-verify.co.in/claim")
        assertEquals(UrlRisk.DANGEROUS, v.risk)
        assertTrue(v.findings.any { it.rule == "BRAND_IMPERSONATION" })
        assertTrue(v.findings.any { it.rule == "LURE_KEYWORD" })
    }

    @Test
    fun `bare IP host is flagged`() {
        val v = UrlThreatHeuristics.analyze("http://45.83.129.11/refund")
        assertTrue(v.findings.any { it.rule == "IP_HOST" })
        assertEquals(UrlRisk.DANGEROUS, v.risk)
    }

    @Test
    fun `apk dropper path is flagged`() {
        val v = UrlThreatHeuristics.analyze("https://some-host.xyz/download/app.apk")
        assertTrue(v.findings.any { it.rule == "REMOTE_PAYLOAD" })
    }

    @Test
    fun `http scheme is flagged`() {
        val v = UrlThreatHeuristics.analyze("http://wikipedia.org")
        assertTrue(v.findings.any { it.rule == "NO_TLS" })
    }

    @Test
    fun `punycode host is flagged`() {
        val v = UrlThreatHeuristics.analyze("https://xn--80ak6aa92e.com/login")
        assertTrue(v.findings.any { it.rule == "PUNYCODE" })
    }

    @Test
    fun `risky tld contributes risk`() {
        val v = UrlThreatHeuristics.analyze("https://some-thing.tk/x")
        assertTrue(v.findings.any { it.rule == "RISKY_TLD" })
    }

    @Test
    fun `credentials in url are flagged`() {
        val v = UrlThreatHeuristics.analyze("https://sbi@evil.example/login")
        assertTrue(v.findings.any { it.rule == "USERINFO" })
    }

    // ── FALSE POSITIVES: these must stay clean ───────────────────────────────
// ── Parsing ─────────────────────────────────────────────────────────────
    @Test
    fun `missing scheme is a parse failure not a silent guess`() {
        val v = UrlThreatHeuristics.analyze("sbi-kyc-update.co.in")
        assertTrue(v.findings.any { it.rule == "PARSE" })
        assertEquals("", v.host)
    }

    @Test
    fun `unsupported scheme rejected`() {
        val v = UrlThreatHeuristics.analyze("javascript://alert(1)")
        assertTrue(v.findings.any { it.rule == "PARSE" })
    }

    @Test
    fun `empty input rejected`() {
        val v = UrlThreatHeuristics.analyze("")
        assertTrue(v.findings.any { it.rule == "PARSE" })
    }

    @Test
    fun `host and port extracted`() {
        val p = UrlThreatHeuristics.parse("https://example.com:8443/a/b")
        assertTrue(p.ok)
        assertEquals("example.com", p.host)
        assertEquals("8443", p.port)
        assertEquals("a/b", p.path)
    }

    @Test
    fun `userinfo detected before at sign`() {
        val p = UrlThreatHeuristics.parse("https://user@example.com/x")
        assertTrue(p.ok)
        assertTrue(p.hasUserInfo)
        assertEquals("example.com", p.host)
    }

    @Test
    fun `query string is not part of host`() {
        val p = UrlThreatHeuristics.parse("https://example.com/p?next=evil.com")
        assertEquals("example.com", p.host)
    }

    @Test
    fun `co in suffix collapses to registrable domain`() {
        assertEquals("sbi.co.in", UrlThreatHeuristics.registrableDomain("www.sbi.co.in"))
        assertEquals("evil.com", UrlThreatHeuristics.registrableDomain("a.b.evil.com"))
    }

    @Test
    fun `host is lowercased`() {
        val p = UrlThreatHeuristics.parse("HTTPS://EXAMPLE.COM/Path")
        assertEquals("example.com", p.host)
        assertEquals("https", p.scheme)
    }

    // ── Scoring / evidence ───────────────────────────────────────────────────
    @Test
    fun `score is bounded 0 to 100`() {
        val v = UrlThreatHeuristics.analyze(
            "http://1.2.3.4:99/user@x-y-z-w-vu.tk/free-gift-otp/download/app.apk?a=1"
        )
        assertTrue("score was ${v.score}", v.score in 0..100)
    }

    @Test
    fun `offline prior is marked as such`() {
        val v = UrlThreatHeuristics.analyze("https://example.com")
        assertEquals(EvidenceSource.OFFLINE_HEURISTIC, v.source)
    }

    @Test
    fun `findings are sorted by weight descending`() {
        val v = UrlThreatHeuristics.analyze("https://sbi-kyc-verify.xyz/login")
        val weights = v.findings.map { it.weight }
        assertEquals(weights.sortedDescending(), weights)
    }

    @Test
    fun `every finding has a rule name and human detail`() {
        val v = UrlThreatHeuristics.analyze("https://hdfc-otp-verify.tk/x")
        assertTrue(v.findings.isNotEmpty())
        v.findings.forEach {
            assertTrue(it.rule.isNotBlank())
            assertTrue(it.detail.isNotBlank())
        }
    }

    @Test
    fun `normalized form is echoed back`() {
        val v = UrlThreatHeuristics.analyze("HTTPS://Example.COM:8443/a/b")
        assertEquals("https://example.com:8443/a/b", v.normalized)
    }

    @Test
    fun `entropy is zero for repeated char and positive for mixed`() {
        assertEquals(0.0, UrlThreatHeuristics.shannonEntropy("aaaa"), 0.0001)
        assertTrue(UrlThreatHeuristics.shannonEntropy("ab3d9fz") > 1.0)
    }

    @Test
    fun `confusable sbi lookalike is caught`() {
        val v = UrlThreatHeuristics.analyze("https://5bi-verify.xyz/")
        assertNotNull(v.host)
        val hit = v.findings.any { it.rule == "HOMOGLYPH" } ||
            v.findings.any { it.rule == "BRAND_IMPERSONATION" }
        assertTrue("lookalike not flagged: ${v.findings}", hit)
    }

    @Test
    fun `homoglyph does not fire on a genuine brand site`() {
        val v = UrlThreatHeuristics.analyze("https://www.sbi.co.in/")
        assertNull(v.findings.firstOrNull { it.rule == "HOMOGLYPH" })
    }

    // ── FALSE POSITIVES: these must stay clean ───────────────────────────
    @Test
    fun `official sbi site is not impersonation`() {
        val v = UrlThreatHeuristics.analyze("https://www.sbi.co.in/")
        assertFalse(v.findings.any { it.rule == "BRAND_IMPERSONATION" })
        assertEquals(UrlRisk.SAFE, v.risk)
    }
    @Test
    fun `official cybercrime portal is safe`() {
        val v = UrlThreatHeuristics.analyze("https://cybercrime.gov.in/")
        assertEquals(UrlRisk.SAFE, v.risk)
    }
    @Test
    fun `wikipedia is safe`() {
        val v = UrlThreatHeuristics.analyze("https://en.wikipedia.org/wiki/Phishing")
        assertEquals(UrlRisk.SAFE, v.risk)
    }
    @Test
    fun `google is safe`() {
        val v = UrlThreatHeuristics.analyze("https://www.google.com/")
        assertEquals(UrlRisk.SAFE, v.risk)
    }
    @Test
    fun `kyc as a path on a legit domain does not trip the lure rule`() {
        // The old scanner flagged ANY url containing "kyc".
        val v = UrlThreatHeuristics.analyze("https://www.wikipedia.org/wiki/KYC")
        assertFalse(v.findings.any { it.rule == "LURE_KEYWORD" })
        assertEquals(UrlRisk.SAFE, v.risk)
    }
}


