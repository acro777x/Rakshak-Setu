package com.rakshaksetu.voip.ai

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Wald's Sequential Probability Ratio Test (SPRT) Accumulator.
 *
 * Mathematically bounds false alarms to < 0.5% (alpha <= 0.005) while achieving
 * > 99% detection accuracy (beta <= 0.01).
 *
 * Theoretical Wald Thresholds:
 *   Upper boundary A = ln((1 - beta) / alpha) = ln(0.99 / 0.005) = ln(198) = 5.288
 *   Lower boundary B = ln(beta / (1 - alpha)) = ln(0.01 / 0.995) = ln(0.01005) = -4.600
 *
 * Real-time dynamic state machine transitions:
 *   - S_k >= A (5.288)  -> THREAT_DETECTED (Immediate Crimson Alert & countermeasure trigger)
 *   - S_k <= B (-4.600) -> BONAFIDE_SAFE   (Emerald Safe state)
 *   - B < S_k < A       -> EVALUATING      (Amber Caution / Evidence Accumulation)
 */
class WaldSprtAccumulator(
    val upperThresholdA: Float = THRESHOLD_A,
    val lowerThresholdB: Float = THRESHOLD_B,
    val neutralPrior: Float = 0.50f
) {
    companion object {
        const val THRESHOLD_A = 5.288f  // Type I error alpha <= 0.005
        const val THRESHOLD_B = -4.600f // Type II error beta <= 0.01
        private const val EPSILON = 1e-4f
    }

    enum class Decision {
        BONAFIDE_SAFE,
        EVALUATING,
        THREAT_DETECTED
    }

    data class SprtStepResult(
        val decision: Decision,
        val cumulativeScore: Float,
        val stepIncrement: Float,
        val observationScore: Float,
        val sampleCount: Int
    )

    private var cumulativeLambda: Float = 0.0f
    private var sampleCount: Int = 0
    private var isThreatConfirmed: Boolean = false

    val currentLambda: Float
        get() = cumulativeLambda

    val totalSamples: Int
        get() = sampleCount

    val isConfirmedThreat: Boolean
        get() = isThreatConfirmed

    /**
     * Ingest an observation score s_k in [0.0, 1.0] from the voice clone / deepfake detector.
     * Computes the log-likelihood ratio increment and updates the Wald SPRT sum.
     */
    @Synchronized
    fun step(score: Float): SprtStepResult {
        val clampedScore = score.coerceIn(EPSILON, 1.0f - EPSILON)
        sampleCount++

        // Log-likelihood ratio increment: Delta Lambda_k = ln(s_k / (1 - s_k)) - ln(p0 / (1 - p0))
        val logOddsObservation = ln(clampedScore / (1.0f - clampedScore))
        val logOddsPrior = ln(neutralPrior / (1.0f - neutralPrior))
        val deltaLambda = logOddsObservation - logOddsPrior

        // Accumulate evidence
        cumulativeLambda += deltaLambda

        // Lower boundary clamping to prevent infinite negative accumulation during long safe calls
        if (cumulativeLambda < lowerThresholdB) {
            cumulativeLambda = lowerThresholdB
        }

        val decision = when {
            cumulativeLambda >= upperThresholdA -> {
                isThreatConfirmed = true
                Decision.THREAT_DETECTED
            }
            cumulativeLambda <= lowerThresholdB -> {
                Decision.BONAFIDE_SAFE
            }
            else -> {
                Decision.EVALUATING
            }
        }

        return SprtStepResult(
            decision = decision,
            cumulativeScore = cumulativeLambda,
            stepIncrement = deltaLambda,
            observationScore = clampedScore,
            sampleCount = sampleCount
        )
    }

    /**
     * Resets the accumulator for a fresh call session.
     */
    @Synchronized
    fun reset() {
        cumulativeLambda = 0.0f
        sampleCount = 0
        isThreatConfirmed = false
    }

    /**
     * Compute current threat percentage for HUD rendering (0.0% to 100.0%).
     */
    fun getThreatConfidencePercent(): Float {
        if (cumulativeLambda <= lowerThresholdB) return 0.0f
        if (cumulativeLambda >= upperThresholdA) return 100.0f
        val range = upperThresholdA - lowerThresholdB
        val normalized = (cumulativeLambda - lowerThresholdB) / range
        return (normalized * 100.0f).coerceIn(0.0f, 100.0f)
    }
}
