package com.rakshaksetu.app.voip

import com.rakshaksetu.app.forensics.Section65BEvidenceManager
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/**
 * Targeted Unit & Integration test for Section 65B forensic manager and HMAC-SHA256 evidence sealing.
 */
class Section65BTest {

    @Test
    fun testHmacSha256Sealing() {
        val evidenceManager = Section65BEvidenceManager(context = null)
        val rawPcm = ByteArray(1024) { 0x3F }
        val key = "CustomSecretKey123".toByteArray(Charsets.UTF_8)

        val hmacHex = evidenceManager.computeHmacSha256Hex(rawPcm, key)
        assertNotNull(hmacHex)
        assertEquals(64, hmacHex.length)

        // Verify repeatability
        val hmacHex2 = evidenceManager.computeHmacSha256Hex(rawPcm, key)
        assertEquals(hmacHex, hmacHex2)

        // Verify tampering changes HMAC
        val tamperedPcm = ByteArray(1024) { 0x40 }
        val tamperedHmacHex = evidenceManager.computeHmacSha256Hex(tamperedPcm, key)
        assertNotEquals(hmacHex, tamperedHmacHex)
    }

    @Test
    fun testManifestIncludesHmacSha256Seal() {
        val evidenceManager = Section65BEvidenceManager(context = null)
        val rawPcm = ByteArray(3200) { 0x1A }
        evidenceManager.updateAudioDigest(rawPcm)

        val manifest = evidenceManager.generateManifest(
            caller = "+919876543210",
            callee = "+911234567890",
            callDurationSeconds = 10,
            transcript = "Your digital arrest warrant is issued",
            threatCategory = "digital_arrest",
            sprtScore = 5.88f,
            isThreat = true,
            scamRisk = 0.95f
        )

        assertNotNull(manifest.hmacSha256Seal)
        assertEquals(64, manifest.hmacSha256Seal.length)
        assertTrue(manifest.isThreatConfirmed)
        assertEquals(3200L, manifest.totalAudioBytesIntercepted)
    }
}
