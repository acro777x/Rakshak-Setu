package com.rakshaksetu.voip

import com.rakshaksetu.voip.evidence.Section65BManifest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * E2E Cryptographic & Legal Forensics Test Suite for Section65BManifest.
 *
 * Verifies Tiers 1 & 2:
 * - SHA-256 audio hash exactness (NIST empty hash: e3b0c442...)
 * - Incremental streaming chunk consistency vs single buffer digest
 * - Forensic seal hash generation: sha256(manifestId|isoDate|caller|callee|audioHash|sprtScore|transcript)
 * - Tamper-evident detection: modifying any field breaks cryptographic seal validation
 * - Ingestion of large audio streams (1,000,000 bytes) in RAM with zero disk I/O
 * - Unicode & multilingual transcript fidelity (Hindi Devanagari text)
 */
class Section65BForensicsTest {

    private lateinit var manifestGenerator: Section65BManifest

    @Before
    fun setUp() {
        // Run with context = null for pure memory test
        manifestGenerator = Section65BManifest(context = null)
    }

    // =========================================================================
    // TIER 1: FEATURE COVERAGE & HASH ACCURACY
    // =========================================================================

    @Test
    fun testEmptyAudioSha256MatchesNistStandardHash() {
        // Standard NIST SHA-256 for 0 bytes input
        val nistEmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val manifest = manifestGenerator.generateManifest(
            caller = "+919876543210",
            callee = "+911234567890",
            callDurationSeconds = 12,
            transcript = "No audio stream captured",
            threatCategory = "safe",
            sprtScore = -4.600f,
            isThreat = false,
            scamRisk = 0.0f
        )

        assertEquals(nistEmptyHash, manifest.pcmAudioSha256)
        assertEquals(0L, manifest.totalAudioBytesIntercepted)
    }

    @Test
    fun testSingleChunkVsMultiChunkStreamingDigestConsistency() {
        val totalSize = 32000 // 1.0s of 16kHz 16-bit PCM
        val audioData = ByteArray(totalSize) { (it * 7 % 256).toByte() }

        // Generator 1: Ingest all 32,000 bytes at once
        val gen1 = Section65BManifest(null)
        gen1.updateAudioDigest(audioData)
        val manifest1 = gen1.generateManifest(
            caller = "peer_A", callee = "peer_B", callDurationSeconds = 5,
            transcript = "test", threatCategory = "safe", sprtScore = 0f,
            isThreat = false, scamRisk = 0f
        )

        // Generator 2: Ingest 32,000 bytes in 10 chunks of 3,200 bytes
        val gen2 = Section65BManifest(null)
        val chunkSize = 3200
        for (i in 0 until totalSize step chunkSize) {
            val chunk = audioData.copyOfRange(i, i + chunkSize)
            gen2.updateAudioDigest(chunk)
        }
        val manifest2 = gen2.generateManifest(
            caller = "peer_A", callee = "peer_B", callDurationSeconds = 5,
            transcript = "test", threatCategory = "safe", sprtScore = 0f,
            isThreat = false, scamRisk = 0f
        )

        assertEquals("Multi-chunk digest must match single-chunk digest", manifest1.pcmAudioSha256, manifest2.pcmAudioSha256)
        assertEquals(totalSize.toLong(), manifest1.totalAudioBytesIntercepted)
        assertEquals(totalSize.toLong(), manifest2.totalAudioBytesIntercepted)

        // Verify against independent java.security.MessageDigest
        val md = MessageDigest.getInstance("SHA-256")
        val expectedHash = md.digest(audioData).joinToString("") { "%02x".format(it) }
        assertEquals(expectedHash, manifest1.pcmAudioSha256)
    }

    @Test
    fun testManifestIdFormattingAndPrefix() {
        val manifest = manifestGenerator.generateManifest(
            caller = "scammer_101", callee = "victim_202", callDurationSeconds = 30,
            transcript = "digital arrest warrant", threatCategory = "digital_arrest",
            sprtScore = 6.5f, isThreat = true, scamRisk = 0.95f
        )

        assertTrue(manifest.manifestId.startsWith("RS65B-"))
        assertEquals(18, manifest.manifestId.length) // RS65B- (6) + 12 hex chars
    }

    @Test
    fun testManifestFieldsCompleteness() {
        val caller = "+919876543210"
        val callee = "+919123456789"
        val duration = 45L
        val transcript = "Aapka digital arrest ho gaya hai CBI officer बोल रहा हूँ"
        val category = "digital_arrest"
        val sprtScore = 5.88f
        val isThreat = true
        val scamRisk = 0.95f

        val manifest = manifestGenerator.generateManifest(
            caller = caller,
            callee = callee,
            callDurationSeconds = duration,
            transcript = transcript,
            threatCategory = category,
            sprtScore = sprtScore,
            isThreat = isThreat,
            scamRisk = scamRisk
        )

        assertEquals(caller, manifest.callerIdentifier)
        assertEquals(callee, manifest.calleeIdentifier)
        assertEquals(duration, manifest.callDurationSeconds)
        assertEquals(transcript, manifest.verbatimTranscript)
        assertEquals(category, manifest.detectedThreatCategory)
        assertEquals(sprtScore, manifest.waldSprtFinalScore, 0.001f)
        assertEquals(isThreat, manifest.isThreatConfirmed)
        assertEquals(scamRisk, manifest.scamRiskScore, 0.001f)
        assertEquals("DTLS 1.2 / SRTP AES_CM_128_HMAC_SHA1_80", manifest.encryptionProtocol)
        assertTrue(manifest.timestampIso.endsWith("Z"))
        assertTrue(manifest.timestampEpochMs > 0)
    }

    @Test
    fun testForensicSealHashComputationExactness() {
        val manifest = manifestGenerator.generateManifest(
            caller = "alice", callee = "bob", callDurationSeconds = 20,
            transcript = "verified clean stream", threatCategory = "safe",
            sprtScore = -4.600f, isThreat = false, scamRisk = 0.0f
        )

        // Expected seal input: "$manifestId|$isoDate|$caller|$callee|$audioHash|$sprtScore|$transcript"
        val expectedSealInput = "${manifest.manifestId}|${manifest.timestampIso}|${manifest.callerIdentifier}|${manifest.calleeIdentifier}|${manifest.pcmAudioSha256}|${manifest.waldSprtFinalScore}|${manifest.verbatimTranscript}"
        val expectedSealHash = MessageDigest.getInstance("SHA-256")
            .digest(expectedSealInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedSealHash, manifest.forensicSealHash)
        assertEquals(64, manifest.forensicSealHash.length)
    }

    // =========================================================================
    // TIER 2: BOUNDARY CASES & TAMPER DETECTION
    // =========================================================================

    @Test
    fun testTamperDetectionSealInvalidation() {
        val manifest = manifestGenerator.generateManifest(
            caller = "cbi_scammer", callee = "victim", callDurationSeconds = 60,
            transcript = "Aap digital arrest mein hain paise bhejo",
            threatCategory = "digital_arrest", sprtScore = 7.2f,
            isThreat = true, scamRisk = 0.98f
        )

        // Tamper 1: Modify transcript
        val tamperedTranscriptInput = "${manifest.manifestId}|${manifest.timestampIso}|${manifest.callerIdentifier}|${manifest.calleeIdentifier}|${manifest.pcmAudioSha256}|${manifest.waldSprtFinalScore}|Tampered transcript without threat"
        val tamperedHash1 = MessageDigest.getInstance("SHA-256")
            .digest(tamperedTranscriptInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertNotEquals(manifest.forensicSealHash, tamperedHash1)

        // Tamper 2: Modify caller
        val tamperedCallerInput = "${manifest.manifestId}|${manifest.timestampIso}|innocent_caller|${manifest.calleeIdentifier}|${manifest.pcmAudioSha256}|${manifest.waldSprtFinalScore}|${manifest.verbatimTranscript}"
        val tamperedHash2 = MessageDigest.getInstance("SHA-256")
            .digest(tamperedCallerInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertNotEquals(manifest.forensicSealHash, tamperedHash2)

        // Tamper 3: Modify audio SHA-256
        val tamperedAudioHash = "${manifest.manifestId}|${manifest.timestampIso}|${manifest.callerIdentifier}|${manifest.calleeIdentifier}|0000000000000000000000000000000000000000000000000000000000000000|${manifest.waldSprtFinalScore}|${manifest.verbatimTranscript}"
        val tamperedHash3 = MessageDigest.getInstance("SHA-256")
            .digest(tamperedAudioHash.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertNotEquals(manifest.forensicSealHash, tamperedHash3)
    }

    @Test
    fun testLargeAudioStreamDigestIntegrity() {
        val oneMegabyte = 1_000_000
        val largePcm = ByteArray(oneMegabyte) { (it xor 0xAA).toByte() }

        manifestGenerator.updateAudioDigest(largePcm)

        val manifest = manifestGenerator.generateManifest(
            caller = "caller_large", callee = "callee_large", callDurationSeconds = 31,
            transcript = "High bandwidth stream", threatCategory = "safe",
            sprtScore = -4.600f, isThreat = false, scamRisk = 0.0f
        )

        assertEquals(oneMegabyte.toLong(), manifest.totalAudioBytesIntercepted)
        assertEquals(64, manifest.pcmAudioSha256.length)

        val md = MessageDigest.getInstance("SHA-256")
        val expectedHash = md.digest(largePcm).joinToString("") { "%02x".format(it) }
        assertEquals(expectedHash, manifest.pcmAudioSha256)
    }

    @Test
    fun testZeroAndNegativeLengthAudioUpdatesIgnored() {
        val dummy = ByteArray(100) { 0x01 }
        manifestGenerator.updateAudioDigest(dummy, length = 0)
        manifestGenerator.updateAudioDigest(dummy, length = -5)

        val manifest = manifestGenerator.generateManifest(
            caller = "user", callee = "peer", callDurationSeconds = 1,
            transcript = "", threatCategory = "safe", sprtScore = 0f,
            isThreat = false, scamRisk = 0f
        )

        assertEquals(0L, manifest.totalAudioBytesIntercepted)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", manifest.pcmAudioSha256)
    }

    @Test
    fun testUnicodeAndDevanagariFidelity() {
        val hindiTranscript = "सीबीआई पुलिस वारंट: सुप्रीम कोर्ट ने आपके अरेस्ट का आदेश दिया है 🚨"
        val manifest = manifestGenerator.generateManifest(
            caller = "सीबीआई_अधिकारी",
            callee = "+919876543210",
            callDurationSeconds = 15,
            transcript = hindiTranscript,
            threatCategory = "digital_arrest",
            sprtScore = 6.2f,
            isThreat = true,
            scamRisk = 0.95f
        )

        assertEquals(hindiTranscript, manifest.verbatimTranscript)
        assertEquals(64, manifest.forensicSealHash.length)
        assertTrue(manifest.isThreatConfirmed)
    }

    @Test
    fun testZeroDurationAndEmptyTranscriptEdgeCase() {
        val manifest = manifestGenerator.generateManifest(
            caller = "", callee = "", callDurationSeconds = 0,
            transcript = "", threatCategory = "unknown", sprtScore = 0.0f,
            isThreat = false, scamRisk = 0.0f
        )

        assertEquals(0L, manifest.callDurationSeconds)
        assertEquals("", manifest.verbatimTranscript)
        assertTrue(manifest.manifestId.startsWith("RS65B-"))
        assertEquals(64, manifest.forensicSealHash.length)
    }

    @Test
    fun testResetRestoresCleanState() {
        val audioBytes = ByteArray(5000) { 0x33 }
        manifestGenerator.updateAudioDigest(audioBytes)

        manifestGenerator.reset()

        val manifest = manifestGenerator.generateManifest(
            caller = "test", callee = "user", callDurationSeconds = 1,
            transcript = "", threatCategory = "safe", sprtScore = 0f,
            isThreat = false, scamRisk = 0f
        )
        assertEquals(0L, manifest.totalAudioBytesIntercepted)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", manifest.pcmAudioSha256)
    }
}
