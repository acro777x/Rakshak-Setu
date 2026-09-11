package com.rakshaksetu.voip

import com.rakshaksetu.voip.evidence.Section65BManifest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit test suite for Section 65B forensic manifest generation and cryptographic hashing.
 */
class Section65BManifestTest {

    @Test
    fun testManifestGenerationAndHashIntegrity() {
        val manifestGen = Section65BManifest(context = null)

        val testAudio = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        manifestGen.updateAudioDigest(testAudio)

        val manifest = manifestGen.generateManifest(
            caller = "+91-9876543210",
            callee = "Sovereign-User",
            callDurationSeconds = 62L,
            transcript = "Main CBI se bol raha hoon digital arrest warrant issue hua hai",
            threatCategory = "Digital Arrest Scam",
            sprtScore = 6.142f,
            isThreat = true,
            scamRisk = 0.95f
        )

        assertNotNull(manifest.manifestId)
        assertTrue(manifest.manifestId.startsWith("RS65B-"))
        assertEquals("+91-9876543210", manifest.callerIdentifier)
        assertEquals(62L, manifest.callDurationSeconds)
        assertTrue(manifest.isThreatConfirmed)

        // SHA-256 of 10 bytes [1..10]
        assertNotNull(manifest.pcmAudioSha256)
        assertEquals(64, manifest.pcmAudioSha256.length) // Hex length of SHA-256 is 64

        // Cryptographic forensic seal
        assertNotNull(manifest.forensicSealHash)
        assertEquals(64, manifest.forensicSealHash.length)
    }
}
