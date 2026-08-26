package com.rakshaksetu.app.pipeline

import org.junit.Assert.*
import org.junit.Test

class DualEngineEnsembleTest {

    private val votingEngine = VotingEngine()

    @Test
    fun ensemble_convictsOnVoiceCloneAttack() {
        val segments = listOf(
            SegmentResult(0, 0, "hello uncle kaise ho", 0.3f, null)
        )
        val cloneResult = CloneDetectorEngine.CloneResult(
            isCloned = true,
            confidence = 0.88f,
            maxSegmentScore = 0.92f,
            avgSegmentScore = 0.84f,
            suspectSegmentIndices = listOf(0),
            speechRate = 3.5f,
            silenceRatio = 0.08f
        )
        val intentResult = IntentPrototypeClassifier.CallIntentResult(
            isScam = false,
            intentScores = mapOf("benign_conversation" to 0.7f),
            distinctThreatCount = 0,
            segmentResults = emptyList()
        )

        val verdict = votingEngine.evaluateEnsemble(segments, intentResult, cloneResult)
        assertTrue("Ensemble must convict when voice cloning is detected", verdict.isScam)
        assertEquals("ai_voice_kidnap", verdict.scamType)
        assertTrue("Confidence should reflect clone score", verdict.confidence >= 0.85f)
    }

    @Test
    fun ensemble_convictsOnUnknownScamViaIntentClassification() {
        val segments = listOf(
            SegmentResult(0, 0, "aapko turant payment karna hoga", 0.4f, null),
            SegmentResult(1, 5, "kisi ko mat batana ye confidential hai", 0.4f, null)
        )
        val cloneResult = CloneDetectorEngine.CloneResult(
            isCloned = false,
            confidence = 0.10f,
            maxSegmentScore = 0.15f,
            avgSegmentScore = 0.10f,
            suspectSegmentIndices = emptyList(),
            speechRate = 3.0f,
            silenceRatio = 0.20f
        )
        val intentResult = IntentPrototypeClassifier.CallIntentResult(
            isScam = true,
            intentScores = mapOf(
                "pressure_urgency" to 0.82f,
                "isolation_tactic" to 0.78f,
                "financial_extraction" to 0.85f
            ),
            distinctThreatCount = 3,
            segmentResults = emptyList(),
            dominantThreat = "financial_extraction",
            dominantThreatScore = 0.85f
        )

        val verdict = votingEngine.evaluateEnsemble(segments, intentResult, cloneResult)
        assertTrue("Ensemble must convict when multiple behavioral threat intents fire", verdict.isScam)
        assertEquals("financial_extraction", verdict.scamType)
        assertEquals(0.85f, verdict.confidence, 0.01f)
    }

    @Test
    fun ensemble_passesBenignCall() {
        val segments = listOf(
            SegmentResult(0, 0, "dinner ready hai aajao", 0.1f, null)
        )
        val cloneResult = CloneDetectorEngine.CloneResult(
            isCloned = false,
            confidence = 0.05f,
            maxSegmentScore = 0.05f,
            avgSegmentScore = 0.05f,
            suspectSegmentIndices = emptyList(),
            speechRate = 2.5f,
            silenceRatio = 0.25f
        )
        val intentResult = IntentPrototypeClassifier.CallIntentResult(
            isScam = false,
            intentScores = mapOf("benign_conversation" to 0.90f),
            distinctThreatCount = 0,
            segmentResults = emptyList()
        )

        val verdict = votingEngine.evaluateEnsemble(segments, intentResult, cloneResult)
        assertFalse("Ensemble must pass benign conversation", verdict.isScam)
    }
}
