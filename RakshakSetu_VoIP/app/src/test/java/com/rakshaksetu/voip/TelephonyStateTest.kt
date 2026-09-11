package com.rakshaksetu.voip

import com.rakshaksetu.voip.ai.AiPipelineCoordinator
import com.rakshaksetu.voip.ai.SpscAudioRingBuffer
import com.rakshaksetu.voip.ai.WaldSprtAccumulator
import com.rakshaksetu.voip.evidence.Section65BManifest
import com.rakshaksetu.voip.telephony.VoipConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

/**
 * E2E Test Suite for Telephony State Transitions, Cross-Feature Pipelines,
 * 1-Tap Countermeasures, and Tier 4 Real-World Application Scenarios (S1-S5).
 *
 * Scenarios verified:
 * - S1: Simulated CBI Digital Arrest Attack (Voice Clone + Scam Keywords -> Crimson Threat <=3s -> Disconnect & Block -> Sealed 65B Manifest)
 * - S2: High-Confidence Deepfake Kidnapping Attack (Synthetic Voice -> SPRT Threat <=3s -> AI Voice Kidnap match)
 * - S3: Authentic Family Conversation (Bonafide Human Voice -> Zero False Alarms -> SPRT clamped at B=-4.600 -> Emerald Safe)
 * - S4: Ambient Noise & Rapid Speech Bursts (Ambiguous p=0.50 -> No false alarm -> SPSC buffer zero underruns)
 * - S5: Section 65B Forensic Seal Verification & Non-Repudiation
 */
class TelephonyStateTest {

    private lateinit var ringBuffer: SpscAudioRingBuffer
    private lateinit var coordinator: AiPipelineCoordinator
    private lateinit var evidenceManager: Section65BManifest

    @Before
    fun setUp() {
        ringBuffer = SpscAudioRingBuffer()
        coordinator = AiPipelineCoordinator(context = null, ringBuffer = ringBuffer)
        evidenceManager = Section65BManifest(context = null)
    }

    // =========================================================================
    // TIER 3: TELEPHONY STATE TRANSITIONS & CROSS-FEATURE COMBINATIONS
    // =========================================================================

    @Test
    fun testOutgoingCallStateLifecycle() {
        var answered = false
        var disconnected = false

        val listener = object : VoipConnection.Listener {
            override fun onCallAnswered(connection: VoipConnection) { answered = true }
            override fun onCallRejected(connection: VoipConnection) {}
            override fun onCallDisconnected(connection: VoipConnection) { disconnected = true }
            override fun onCallHoldStateChanged(connection: VoipConnection, onHold: Boolean) {}
        }

        val connection = VoipConnection(
            peerIdentifier = "+919876543210",
            isIncoming = false,
            callbacks = listener
        )

        assertFalse(connection.isIncoming)
        assertEquals("+919876543210", connection.peerIdentifier)

        // Simulate remote answer
        connection.onAnswer()
        assertTrue(answered)

        // Simulate local disconnect
        connection.onDisconnect()
        assertTrue(disconnected)
    }

    @Test
    fun testIncomingCallStateLifecycleAndRejection() {
        var rejected = false

        val listener = object : VoipConnection.Listener {
            override fun onCallAnswered(connection: VoipConnection) {}
            override fun onCallRejected(connection: VoipConnection) { rejected = true }
            override fun onCallDisconnected(connection: VoipConnection) {}
            override fun onCallHoldStateChanged(connection: VoipConnection, onHold: Boolean) {}
        }

        val connection = VoipConnection(
            peerIdentifier = "scammer_peer_99",
            isIncoming = true,
            callbacks = listener
        )

        assertTrue(connection.isIncoming)
        assertEquals("scammer_peer_99", connection.peerIdentifier)

        // Simulate call rejection
        connection.onReject()
        assertTrue(rejected)
    }

    @Test
    fun testCallHoldAndUnholdTransitions() {
        var holdState: Boolean? = null

        val listener = object : VoipConnection.Listener {
            override fun onCallAnswered(connection: VoipConnection) {}
            override fun onCallRejected(connection: VoipConnection) {}
            override fun onCallDisconnected(connection: VoipConnection) {}
            override fun onCallHoldStateChanged(connection: VoipConnection, onHold: Boolean) {
                holdState = onHold
            }
        }

        val connection = VoipConnection("peer_123", isIncoming = false, callbacks = listener)

        connection.onHold()
        assertEquals(true, holdState)

        connection.onUnhold()
        assertEquals(false, holdState)
    }

    @Test
    fun testCrossFeatureAudioToSprtThreatTrigger() = runBlocking {
        // Feed synthetic voice probability into coordinator accumulator
        val step1 = coordinator.sprtAccumulator.step(0.95f)
        assertEquals(WaldSprtAccumulator.Decision.EVALUATING, step1.decision)

        // Step 2 triggers Wald A = 5.288 threshold
        val step2 = coordinator.sprtAccumulator.step(0.95f)
        assertEquals(WaldSprtAccumulator.Decision.THREAT_DETECTED, step2.decision)
        assertTrue(coordinator.sprtAccumulator.isConfirmedThreat)
        assertTrue(coordinator.sprtAccumulator.currentLambda >= 5.288f)
    }

    @Test
    fun testCrossFeatureAsrPartialsToScamTrieKeywordHighlights() = runBlocking {
        val scamTranscript = "Aap digital arrest ho gaye hain, CBI inspector bol raha hoon"
        coordinator.asrEngine.injectSimulatedTranscript(scamTranscript)

        val state = coordinator.uiState.value
        assertEquals(AiPipelineCoordinator.ThreatLevel.CRITICAL_THREAT, state.threatLevel)
        assertTrue(state.scamRiskScore >= 0.70f)
        assertTrue(state.matchedSpans.isNotEmpty())
        assertEquals(scamTranscript, state.liveTranscript)
    }

    @Test
    fun testOneTapCountermeasureDisconnectAndSealManifest() {
        // 1. Simulate active call with audio frames
        val audioFrames = ByteArray(16000) { 0x42 }
        evidenceManager.updateAudioDigest(audioFrames)

        // 2. Simulate threat detection
        val transcript = "Your account is frozen for money laundering by CBI cyber crime branch"
        coordinator.asrEngine.injectSimulatedTranscript(transcript)

        // 3. User taps 1-tap "Disconnect & Block Scammer"
        coordinator.stop()
        coordinator.markTamperSealed()

        val manifest = evidenceManager.generateManifest(
            caller = "cbi_fraudster",
            callee = "innocent_victim",
            callDurationSeconds = 25,
            transcript = transcript,
            threatCategory = "digital_arrest",
            sprtScore = 6.42f,
            isThreat = true,
            scamRisk = 0.95f
        )

        // Verify evidence is sealed
        assertTrue(coordinator.uiState.value.isTamperSealed)
        assertFalse(coordinator.uiState.value.isCallActive)
        assertEquals(16000L, manifest.totalAudioBytesIntercepted)
        assertTrue(manifest.isThreatConfirmed)
        assertEquals("digital_arrest", manifest.detectedThreatCategory)
        assertEquals(64, manifest.forensicSealHash.length)
    }

    // =========================================================================
    // TIER 4: REAL-WORLD APPLICATION SCENARIOS (S1 - S5)
    // =========================================================================

    /**
     * Scenario S1: Simulated CBI Digital Arrest Call
     *
     * Flow:
     * 1. Incoming call from unknown peer pretending to be CBI officer.
     * 2. Audio intercepted in RAM; AI voice clone detector flags synthetic vocal artifacts (p = 0.95).
     * 3. Within <= 3.0 seconds (2 observations), Wald SPRT crosses A = 5.288.
     * 4. ASR emits live transcript: "Main CBI cyber cell se bol raha hoon aapka digital arrest warrant nikla hai".
     * 5. Scam Phrase Trie triggers category digital_arrest.
     * 6. HUD transitions from Emerald Safe to Crimson Threat.
     * 7. User taps "Disconnect & Block Scammer" (1-tap defense).
     * 8. Section 65B forensic manifest generated with valid SHA-256 cryptographic seal.
     */
    @Test
    fun testScenarioS1SimulatedCbiDigitalArrestAttack() = runBlocking {
        // Step 1: Establish Call Connection
        var callDisconnected = false
        val connection = VoipConnection(
            peerIdentifier = "+919988776655",
            isIncoming = true,
            callbacks = object : VoipConnection.Listener {
                override fun onCallAnswered(c: VoipConnection) {}
                override fun onCallRejected(c: VoipConnection) {}
                override fun onCallDisconnected(c: VoipConnection) { callDisconnected = true }
                override fun onCallHoldStateChanged(c: VoipConnection, onHold: Boolean) {}
            }
        )
        connection.onAnswer()

        // Step 2: Intercept in-flight audio frames into SPSC Ring Buffer & Evidence Digest
        val pcmAudio = ByteArray(48000) { (it * 13 % 256).toByte() } // 3.0s audio @ 16kHz
        val written = ringBuffer.write(pcmAudio)
        assertEquals(48000, written)
        evidenceManager.updateAudioDigest(pcmAudio)

        // Step 3: Neural Voice Clone Detection -> Wald SPRT accumulates evidence
        // Observation 1 (t = 1.5s): high spoof probability
        val step1 = coordinator.sprtAccumulator.step(0.95f)
        assertEquals(WaldSprtAccumulator.Decision.EVALUATING, step1.decision)
        assertFalse(coordinator.sprtAccumulator.isConfirmedThreat)

        // Observation 2 (t = 3.0s): triggers Wald A = 5.288 within statutory <=3.0s bound
        val step2 = coordinator.sprtAccumulator.step(0.95f)
        assertEquals(WaldSprtAccumulator.Decision.THREAT_DETECTED, step2.decision)
        assertTrue(step2.cumulativeScore >= 5.288f)
        assertTrue(coordinator.sprtAccumulator.isConfirmedThreat)

        // Step 4: Streaming ASR emits transcript
        val cbiScamTranscript = "Main CBI cyber cell se inspector bol raha hoon aapka digital arrest warrant nikla hai video call disconnect mat karna"
        coordinator.asrEngine.injectSimulatedTranscript(cbiScamTranscript)

        // Step 5 & 6: HUD State reflects Crimson Threat
        val uiState = coordinator.uiState.value
        assertEquals(AiPipelineCoordinator.ThreatLevel.CRITICAL_THREAT, uiState.threatLevel)
        assertTrue(uiState.matchedSpans.isNotEmpty())
        assertTrue(uiState.scamRiskScore >= 0.70f)

        // Step 7: User executes 1-tap "Disconnect & Block Scammer"
        connection.onDisconnect()
        assertTrue("Call must be severed immediately", callDisconnected)
        coordinator.stop()
        coordinator.markTamperSealed()

        // Step 8: Seal Section 65B Forensic Manifest Dossier
        val manifest = evidenceManager.generateManifest(
            caller = connection.peerIdentifier,
            callee = "+919876543210",
            callDurationSeconds = 3,
            transcript = cbiScamTranscript,
            threatCategory = "digital_arrest",
            sprtScore = step2.cumulativeScore,
            isThreat = true,
            scamRisk = uiState.scamRiskScore
        )

        // Cryptographic Non-Repudiation Assertions
        assertEquals(48000L, manifest.totalAudioBytesIntercepted)
        assertEquals(connection.peerIdentifier, manifest.callerIdentifier)
        assertTrue(manifest.isThreatConfirmed)
        assertEquals("digital_arrest", manifest.detectedThreatCategory)
        assertEquals(64, manifest.pcmAudioSha256.length)
        assertEquals(64, manifest.forensicSealHash.length)

        // Verify independent SHA-256 match on audio frames
        val expectedAudioSha = MessageDigest.getInstance("SHA-256")
            .digest(pcmAudio).joinToString("") { "%02x".format(it) }
        assertEquals(expectedAudioSha, manifest.pcmAudioSha256)
    }

    /**
     * Scenario S2: High-Confidence Deepfake Kidnapping Call
     *
     * Ingests near-certain cloned voice ($p = 0.995$) and emergency kidnapping extortion phrase.
     * SPRT boundary A=5.288 is crossed on Observation 1.
     */
    @Test
    fun testScenarioS2HighConfidenceDeepfakeKidnappingCall() = runBlocking {
        // Cloned voice model gives high score on initial 3.0s window
        val sprtResult = coordinator.sprtAccumulator.step(0.996f)
        assertEquals(WaldSprtAccumulator.Decision.THREAT_DETECTED, sprtResult.decision)
        assertTrue(coordinator.sprtAccumulator.isConfirmedThreat)
        assertTrue(sprtResult.cumulativeScore >= coordinator.sprtAccumulator.upperThresholdA)

        // ASR emits extortion plea
        val kidnapTranscript = "Papa mujhe bachao police ne pakad liya rape case mein accident ho gaya paise bhejo"
        coordinator.asrEngine.injectSimulatedTranscript(kidnapTranscript)

        val state = coordinator.uiState.value
        assertEquals(AiPipelineCoordinator.ThreatLevel.CRITICAL_THREAT, state.threatLevel)
        assertTrue(state.matchedSpans.any { it.text.contains("papa mujhe bachao", ignoreCase = true) })
        assertEquals(100.0f, coordinator.sprtAccumulator.getThreatConfidencePercent(), 0.01f)
    }

    /**
     * Scenario S3: Authentic Family Conversation (Bonafide Human Voice)
     *
     * Tests 25 consecutive evaluation windows with genuine speech ($p \le 0.05$).
     * Verifies SPRT accumulator stays clamped at or below lower bound B = -4.600,
     * maintaining Emerald Safe state with zero false alarms ($\alpha < 0.005$).
     */
    @Test
    fun testScenarioS3AuthenticFamilyConversationZeroFalseAlarms() = runBlocking {
        val genuineSpeechProbabilities = floatArrayOf(
            0.02f, 0.04f, 0.01f, 0.03f, 0.05f, 0.02f, 0.03f, 0.01f,
            0.04f, 0.02f, 0.03f, 0.01f, 0.02f, 0.05f, 0.03f, 0.02f,
            0.01f, 0.04f, 0.02f, 0.03f, 0.01f, 0.02f, 0.04f, 0.03f, 0.02f
        )

        genuineSpeechProbabilities.forEachIndexed { index, prob ->
            val result = coordinator.sprtAccumulator.step(prob)
            if (index == 0) {
                // First observation of p=0.02 provides Delta Lambda = -3.89f, which is EVALUATING (not yet <= -4.600f)
                assertEquals("Observation 0 is accumulating initial evidence",
                    WaldSprtAccumulator.Decision.EVALUATING, result.decision)
            } else {
                assertEquals("Observation $index must be classified as BONAFIDE_SAFE",
                    WaldSprtAccumulator.Decision.BONAFIDE_SAFE, result.decision)
                assertEquals(-4.600f, result.cumulativeScore, 0.001f)
            }
            assertFalse(coordinator.sprtAccumulator.isConfirmedThreat)
        }

        // Feed genuine conversational text into ASR
        coordinator.asrEngine.injectSimulatedTranscript("Hello, are you coming home early for dinner tonight? We made paneer.")

        val state = coordinator.uiState.value
        assertEquals(AiPipelineCoordinator.ThreatLevel.SAFE, state.threatLevel)
        assertEquals(0.0f, state.scamRiskScore, 0.0f)
        assertTrue(state.matchedSpans.isEmpty())
        assertEquals(0.0f, coordinator.sprtAccumulator.getThreatConfidencePercent(), 0.001f)
    }

    /**
     * Scenario S4: Ambient Noise & Rapid Audio Chunks
     *
     * Tests ambiguous noisy audio frames (p = 0.50, LLR = 0) where accumulator stays in EVALUATING
     * zone without false positive. SPSC ring buffer processes bursts without underrun.
     */
    @Test
    fun testScenarioS4AmbientNoiseAndRapidAudioChunks() {
        // 1. Ingest 10 neutral frames (p = 0.50)
        for (i in 0 until 10) {
            val result = coordinator.sprtAccumulator.step(0.50f)
            assertEquals(WaldSprtAccumulator.Decision.EVALUATING, result.decision)
            assertEquals(0.0f, result.stepIncrement, 0.001f)
            assertFalse(coordinator.sprtAccumulator.isConfirmedThreat)
        }

        // 2. Rapid audio chunks written to SPSC ring buffer
        val burstChunks = 15
        val chunkSize = 3200 // 100ms
        for (i in 0 until burstChunks) {
            val chunk = ByteArray(chunkSize) { (it + i).toByte() }
            val written = ringBuffer.write(chunk)
            assertEquals(chunkSize, written)

            val dest = ByteArray(chunkSize)
            val read = ringBuffer.read(dest)
            assertEquals(chunkSize, read)
            assertArrayEquals(chunk, dest)
        }
        assertEquals(0, ringBuffer.available())
    }

    /**
     * Scenario S5: Section 65B Forensic Seal Verification & Non-Repudiation
     *
     * Validates that forensic seal computed during live call can be independently verified
     * and guarantees tamper-evident non-repudiation in a legal proceeding.
     */
    @Test
    fun testScenarioS5Section65BForensicsNonRepudiationSealVerification() {
        val audioBytes = ByteArray(32000) { (it % 100).toByte() }
        evidenceManager.updateAudioDigest(audioBytes)

        val transcript = "Digital arrest notice under Section 420 IPC"
        val manifest = evidenceManager.generateManifest(
            caller = "+919123456780",
            callee = "+919876543210",
            callDurationSeconds = 18,
            transcript = transcript,
            threatCategory = "digital_arrest",
            sprtScore = 5.92f,
            isThreat = true,
            scamRisk = 0.95f
        )

        // Courtroom Independent Verification Process:
        // 1. Recompute Audio SHA-256
        val md = MessageDigest.getInstance("SHA-256")
        val recalculatedAudioHash = md.digest(audioBytes).joinToString("") { "%02x".format(it) }
        assertEquals("Audio hash must match original byte stream", recalculatedAudioHash, manifest.pcmAudioSha256)

        // 2. Recompute Forensic Seal
        val courtroomSealInput = "${manifest.manifestId}|${manifest.timestampIso}|${manifest.callerIdentifier}|${manifest.calleeIdentifier}|${recalculatedAudioHash}|${manifest.waldSprtFinalScore}|${manifest.verbatimTranscript}"
        val recalculatedSealHash = MessageDigest.getInstance("SHA-256")
            .digest(courtroomSealInput.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals("Forensic seal must match statutory affidavit", recalculatedSealHash, manifest.forensicSealHash)
    }
}
