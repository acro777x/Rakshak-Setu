package com.rakshaksetu.voip

import com.rakshaksetu.voip.ai.WaldSprtAccumulator
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * E2E & Mathematical Unit Test Suite for Wald's Sequential Probability Ratio Test (SPRT) Accumulator.
 *
 * Verifies Tiers 1 & 2:
 * - Mathematical derivation of thresholds: A = ln((1-beta)/alpha) = 5.288, B = ln(beta/(1-alpha)) = -4.600
 * - Error bounds: alpha <= 0.005 (Type I false alarm), beta <= 0.010 (Type II miss rate, >99% power)
 * - Single-step LLR logarithmic increment accuracy
 * - Time-to-threat trigger <= 3.0 seconds (Observation 1 or 2 with 3.0s window / 1.5s stride)
 * - Clamping invariants to prevent negative score drift during bonafide human conversations
 * - Extreme probability handling (p=0.0, p=1.0, NaN resistance via epsilon)
 * - Threat confidence percentage mapping (0.0% to 100.0%)
 */
class WaldSprtAccumulatorTest {

    private lateinit var accumulator: WaldSprtAccumulator
    private val delta = 0.002f

    @Before
    fun setUp() {
        accumulator = WaldSprtAccumulator()
    }

    // =========================================================================
    // TIER 1: MATHEMATICAL SPECIFICATIONS & CORE TRANSITIONS
    // =========================================================================

    @Test
    fun testExactWaldThresholdDerivations() {
        val alpha = 0.005  // Max allowable false alarm rate (< 0.5%)
        val beta = 0.010   // Max allowable miss rate (<= 1.0%, >99% power)

        val theoreticalA = ln((1.0 - beta) / alpha) // ln(0.99 / 0.005) = ln(198) = 5.288267
        val theoreticalB = ln(beta / (1.0 - alpha)) // ln(0.01 / 0.995) = ln(0.01005) = -4.59998

        assertEquals(5.288f, accumulator.upperThresholdA, delta)
        assertEquals(-4.600f, accumulator.lowerThresholdB, delta)
        assertEquals(theoreticalA.toFloat(), accumulator.upperThresholdA, delta)
        assertEquals(theoreticalB.toFloat(), accumulator.lowerThresholdB, delta)
    }

    @Test
    fun testInitialStateInvariants() {
        assertEquals(0.0f, accumulator.currentLambda, delta)
        assertEquals(0, accumulator.totalSamples)
        assertFalse(accumulator.isConfirmedThreat)
        assertEquals(0.50f, accumulator.neutralPrior, delta)
    }

    @Test
    fun testSingleStepLlrCalculationAccuracy() {
        // Score = 0.80 -> logOddsObservation = ln(0.80 / 0.20) = ln(4) approx 1.38629f
        // Prior = 0.50 -> logOddsPrior = ln(0.50 / 0.50) = 0.0f
        // Expected Delta Lambda = 1.38629f
        val result = accumulator.step(0.80f)

        assertEquals(1, accumulator.totalSamples)
        assertEquals(1.386f, result.stepIncrement, delta)
        assertEquals(1.386f, result.cumulativeScore, delta)
        assertEquals(WaldSprtAccumulator.Decision.EVALUATING, result.decision)
        assertFalse(accumulator.isConfirmedThreat)
    }

    @Test
    fun testBonafideSafeDecisionAndLowerBoundaryClamping() {
        // Genuine voice scores: 0.05 -> Delta Lambda = ln(0.05 / 0.95) approx -2.9444f
        val step1 = accumulator.step(0.05f)
        assertEquals(WaldSprtAccumulator.Decision.EVALUATING, step1.decision)
        assertEquals(-2.944f, step1.cumulativeScore, delta)

        // Step 2: Cumulative would be -5.888f, which crosses lower bound B = -4.600f
        val step2 = accumulator.step(0.05f)
        assertEquals(WaldSprtAccumulator.Decision.BONAFIDE_SAFE, step2.decision)
        // Must be clamped to B (-4.600f) to prevent unbounded negative accumulation
        assertEquals(-4.600f, step2.cumulativeScore, delta)
        assertEquals(-4.600f, accumulator.currentLambda, delta)
        assertFalse(accumulator.isConfirmedThreat)
    }

    @Test
    fun testDeepfakeDetectionWithinThreeSecondsThreshold() {
        // High confidence deepfake voice clone (p = 0.95)
        // Step 1 (t = 1.5s): Delta Lambda = ln(0.95 / 0.05) = ln(19) approx 2.9444f
        val step1 = accumulator.step(0.95f)
        assertEquals(WaldSprtAccumulator.Decision.EVALUATING, step1.decision)
        assertEquals(2.944f, step1.cumulativeScore, delta)
        assertFalse(accumulator.isConfirmedThreat)

        // Step 2 (t = 3.0s): Cumulative = 2.9444 + 2.9444 = 5.8888f >= 5.288f (Threshold A)
        val step2 = accumulator.step(0.95f)
        assertEquals(WaldSprtAccumulator.Decision.THREAT_DETECTED, step2.decision)
        assertTrue(step2.cumulativeScore >= accumulator.upperThresholdA)
        assertTrue(accumulator.isConfirmedThreat)
        assertEquals(2, accumulator.totalSamples)
    }

    @Test
    fun testExtremeDeepfakeSingleStepTrigger() {
        // Near-certain synthetic spoof: p = 0.996
        // Delta Lambda = ln(0.996 / 0.004) = ln(249) approx 5.517f >= 5.288f
        val result = accumulator.step(0.996f)
        assertEquals(WaldSprtAccumulator.Decision.THREAT_DETECTED, result.decision)
        assertTrue(result.cumulativeScore >= 5.288f)
        assertTrue(accumulator.isConfirmedThreat)
        assertEquals(1, accumulator.totalSamples)
    }

    @Test
    fun testAccumulatorResetRestoresCleanState() {
        accumulator.step(0.996f)
        assertTrue(accumulator.isConfirmedThreat)
        assertTrue(accumulator.currentLambda >= accumulator.upperThresholdA)

        accumulator.reset()

        assertEquals(0.0f, accumulator.currentLambda, delta)
        assertEquals(0, accumulator.totalSamples)
        assertFalse(accumulator.isConfirmedThreat)
        assertEquals(46.52f, accumulator.getThreatConfidencePercent(), 0.5f)
    }

    // =========================================================================
    // TIER 2: BOUNDARY VALUES, CORNER CASES & NUMERICAL STABILITY
    // =========================================================================

    @Test
    fun testLongBonafideConversationDoesNotAccumulateInfiniteNegativeDrift() {
        // Step 1: p = 0.02 -> Delta Lambda = ln(0.02/0.98) = -3.89f (EVALUATING, not yet <= -4.600)
        val firstStep = accumulator.step(0.02f)
        assertEquals(WaldSprtAccumulator.Decision.EVALUATING, firstStep.decision)
        assertEquals(-3.892f, firstStep.cumulativeScore, delta)

        // Steps 2..50: Crosses B (-4.600f) and remains clamped at -4.600f
        for (i in 1 until 50) {
            val res = accumulator.step(0.02f)
            assertEquals(WaldSprtAccumulator.Decision.BONAFIDE_SAFE, res.decision)
            assertEquals(-4.600f, res.cumulativeScore, delta)
        }
        assertEquals(-4.600f, accumulator.currentLambda, delta)

        // If an attacker now injects a strong fake voice (p = 0.98, Delta = ln(0.98/0.02) = ln(49) = 3.89f),
        // recovery starts from -4.600 rather than -200, allowing detection in timely fashion.
        val recoveryStep1 = accumulator.step(0.98f) // -4.600 + 3.89 = -0.71f
        assertEquals(-0.708f, recoveryStep1.cumulativeScore, delta)
    }

    @Test
    fun testZeroProbabilityInputClampedSafelyWithoutNaNOrInfinity() {
        // Input p = 0.0f must be clamped to EPSILON (1e-4) -> ln(1e-4 / 0.9999) approx -9.21f
        val result = accumulator.step(0.0f)
        assertFalse(result.stepIncrement.isNaN())
        assertFalse(result.stepIncrement.isInfinite())
        assertEquals(WaldSprtAccumulator.Decision.BONAFIDE_SAFE, result.decision)
        assertEquals(-4.600f, result.cumulativeScore, delta)
    }

    @Test
    fun testOneProbabilityInputClampedSafelyWithoutNaNOrInfinity() {
        // Input p = 1.0f must be clamped to 1 - EPSILON -> ln(0.9999 / 1e-4) approx +9.21f
        val result = accumulator.step(1.0f)
        assertFalse(result.stepIncrement.isNaN())
        assertFalse(result.stepIncrement.isInfinite())
        assertEquals(WaldSprtAccumulator.Decision.THREAT_DETECTED, result.decision)
        assertTrue(result.cumulativeScore >= 5.288f)
        assertTrue(accumulator.isConfirmedThreat)
    }

    @Test
    fun testNeutralObservationDoesNotAlterScore() {
        // Input p = 0.50f represents total ambiguity -> Delta Lambda = ln(0.50 / 0.50) = 0.0f
        val initialScore = accumulator.currentLambda
        val result = accumulator.step(0.50f)

        assertEquals(0.0f, result.stepIncrement, delta)
        assertEquals(initialScore, result.cumulativeScore, delta)
        assertEquals(WaldSprtAccumulator.Decision.EVALUATING, result.decision)
    }

    @Test
    fun testThreatConfidencePercentMonotonicity() {
        // At or below lower threshold: 0%
        accumulator.step(0.01f)
        accumulator.step(0.01f)
        assertEquals(0.0f, accumulator.getThreatConfidencePercent(), delta)

        // At midpoint lambda = 0: (0 - (-4.600)) / (5.288 - (-4.600)) = 4.600 / 9.888 = 46.52%
        accumulator.reset()
        assertEquals(46.52f, accumulator.getThreatConfidencePercent(), 0.5f)

        // Above upper threshold: 100%
        accumulator.step(0.999f)
        assertEquals(100.0f, accumulator.getThreatConfidencePercent(), delta)
    }

    @Test
    fun testCustomThresholdConstructor() {
        val custom = WaldSprtAccumulator(
            upperThresholdA = 4.0f,
            lowerThresholdB = -3.0f,
            neutralPrior = 0.40f
        )
        assertEquals(4.0f, custom.upperThresholdA, delta)
        assertEquals(-3.0f, custom.lowerThresholdB, delta)
        assertEquals(0.40f, custom.neutralPrior, delta)
    }
}
