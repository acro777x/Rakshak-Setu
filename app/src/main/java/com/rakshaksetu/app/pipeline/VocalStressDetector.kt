package com.rakshaksetu.app.pipeline

import android.util.Log
import kotlin.math.*

/**
 * Psychological Urgency & Vocal Stress Intimidation Detector
 *
 * Scammers in high-pressure social engineering attacks (e.g. Digital Arrest, Virtual Kidnapping)
 * exhibit distinctive acoustic stress patterns:
 * - High-frequency energy shifts (1.5 kHz - 4.0 kHz boost during shouting/intimidation)
 * - Zero-crossing rate (ZCR) spikes during aggressive commanding
 * - Abrupt intensity / RMS bursts without normal conversational turn-taking
 */
object VocalStressDetector {
    private const val TAG = "VocalStressDetector"
    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE = 320

    data class StressAssessment(
        val stressScore: Float,         // 0.0 (calm/neutral) to 1.0 (severe intimidation/panic)
        val highFreqRatio: Float,       // Energy above 1.5kHz vs total energy
        val averageZcr: Float,          // Zero crossing rate
        val isAggressiveTone: Boolean   // High likelihood of hostile caller intimidation
    )

    fun evaluateStress(pcmData: ByteArray): StressAssessment {
        val samples = pcmToFloat(pcmData)
        if (samples.size < FRAME_SIZE) {
            return StressAssessment(0f, 0f, 0f, false)
        }

        var zeroCrossings = 0
        var totalEnergy = 0.0f
        var highFreqEnergy = 0.0f

        // 1. Zero Crossing Rate (ZCR)
        for (i in 1 until samples.size) {
            if ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0)) {
                zeroCrossings++
            }
        }
        val avgZcr = zeroCrossings.toFloat() / samples.size

        // 2. High-Frequency vs Total Energy Ratio via Spectral Pre-emphasis Filter
        // High frequency band (1.5kHz - 4.0kHz) intensifies significantly under screaming / aggressive speech
        for (i in 1 until samples.size) {
            val s = samples[i]
            val diff = s - 0.95f * samples[i - 1] // First-order high-pass approximation
            totalEnergy += s * s
            highFreqEnergy += diff * diff
        }

        val hfRatio = if (totalEnergy > 1e-4f) (highFreqEnergy / totalEnergy).coerceIn(0f, 2f) else 0f

        // 3. Stress Index Synthesis
        var score = 0.0f
        if (hfRatio > 0.65f) score += 0.40f
        if (avgZcr > 0.12f) score += 0.30f
        if (hfRatio > 0.85f && avgZcr > 0.15f) score += 0.30f

        val finalScore = score.coerceIn(0f, 1f)
        val isAggressive = finalScore >= 0.70f

        Log.d(TAG, "VocalStress: score=%.2f, hfRatio=%.2f, zcr=%.3f, aggressive=%s"
            .format(finalScore, hfRatio, avgZcr, isAggressive))

        return StressAssessment(
            stressScore = finalScore,
            highFreqRatio = hfRatio,
            averageZcr = avgZcr,
            isAggressiveTone = isAggressive
        )
    }

    private fun pcmToFloat(pcmData: ByteArray): FloatArray {
        val numSamples = pcmData.size / 2
        val floatSamples = FloatArray(numSamples)
        for (i in 0 until numSamples) {
            val low = pcmData[i * 2].toInt() and 0xFF
            val high = pcmData[i * 2 + 1].toInt()
            val sample16 = (high shl 8) or low
            floatSamples[i] = (sample16.toShort().toFloat() / 32768.0f).coerceIn(-1.0f, 1.0f)
        }
        return floatSamples
    }
}
