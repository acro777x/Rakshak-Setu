package com.rakshaksetu.app.pipeline

import android.util.Log
import kotlin.math.*

/**
 * Prosody and Behavioral Speech Dynamics Analyzer
 * Models speech rhythm, pitch contours (F0), microvariations (jitter/shimmer),
 * and pauses to differentiate natural human vocal tract acoustics from neural TTS outputs.
 */
object ProsodyAnalyzer {
    private const val TAG = "ProsodyAnalyzer"
    private const val SAMPLE_RATE = 16000
    private const val FRAME_SIZE = 320 // 20ms frame
    private const val HOP_SIZE = 160   // 10ms hop
    private const val MIN_F0 = 60.0f   // Min vocal pitch 60Hz
    private const val MAX_F0 = 450.0f  // Max vocal pitch 450Hz

    data class ProsodyMetrics(
        val meanF0: Float,
        val f0StdDev: Float,
        val jitterLocal: Float,      // Pitch period perturbation (<0.005 for neural TTS, 0.01-0.04 for humans)
        val shimmerLocal: Float,     // Amplitude perturbation (<0.02 for neural TTS, 0.03-0.08 for humans)
        val hnrDb: Float,            // Harmonics-to-noise ratio in dB
        val ttsAnomalyScore: Float   // 0.0 (natural human) to 1.0 (highly synthetic prosody)
    )

    /**
     * Analyzes PCM audio (16-bit 16kHz mono) for prosody and microvariation anomalies.
     */
    fun analyze(pcmData: ByteArray): ProsodyMetrics {
        val samples = pcmToFloat(pcmData)
        if (samples.size < SAMPLE_RATE * 0.5f) { // Need at least 500ms
            return ProsodyMetrics(0f, 0f, 0f, 0f, 0f, 0f)
        }

        val numFrames = (samples.size - FRAME_SIZE) / HOP_SIZE + 1
        val pitchTrack = mutableListOf<Float>()
        val peakAmplitudes = mutableListOf<Float>()

        val minLag = (SAMPLE_RATE / MAX_F0).toInt()
        val maxLag = (SAMPLE_RATE / MIN_F0).toInt()

        for (f in 0 until numFrames) {
            val offset = f * HOP_SIZE
            var maxVal = 0.0f
            for (i in 0 until FRAME_SIZE) {
                val s = abs(samples[offset + i])
                if (s > maxVal) maxVal = s
            }
            peakAmplitudes.add(maxVal)

            // Autocorrelation pitch estimation
            var bestLag = 0
            var maxCorr = 0.0f
            var energy = 0.0f

            for (i in 0 until FRAME_SIZE) {
                val s = samples[offset + i]
                energy += s * s
            }

            if (energy > 1e-4f) {
                for (lag in minLag..maxLag) {
                    var corr = 0.0f
                    for (i in 0 until (FRAME_SIZE - lag)) {
                        corr += samples[offset + i] * samples[offset + i + lag]
                    }
                    if (corr > maxCorr) {
                        maxCorr = corr
                        bestLag = lag
                    }
                }

                val normalizedCorr = maxCorr / energy
                if (normalizedCorr > 0.40f && bestLag > 0) {
                    val f0 = SAMPLE_RATE.toFloat() / bestLag.toFloat()
                    pitchTrack.add(f0)
                }
            }
        }

        if (pitchTrack.size < 5) {
            return ProsodyMetrics(0f, 0f, 0f, 0f, 0f, 0f)
        }

        // 1. Pitch Statistics
        val meanF0 = pitchTrack.average().toFloat()
        val f0Variance = pitchTrack.map { (it - meanF0).pow(2) }.average().toFloat()
        val f0StdDev = sqrt(f0Variance)

        // 2. Jitter Local (Cycle-to-cycle F0 perturbation)
        var jitterSum = 0.0f
        for (i in 0 until pitchTrack.size - 1) {
            jitterSum += abs(pitchTrack[i] - pitchTrack[i + 1])
        }
        val jitterLocal = if (meanF0 > 0) (jitterSum / (pitchTrack.size - 1)) / meanF0 else 0f

        // 3. Shimmer Local (Cycle-to-cycle amplitude perturbation)
        var shimmerSum = 0.0f
        val validPeaks = peakAmplitudes.filter { it > 0.01f }
        val meanAmp = if (validPeaks.isNotEmpty()) validPeaks.average().toFloat() else 1f

        if (validPeaks.size > 1 && meanAmp > 0) {
            for (i in 0 until validPeaks.size - 1) {
                shimmerSum += abs(validPeaks[i] - validPeaks[i + 1])
            }
        }
        val shimmerLocal = if (validPeaks.size > 1) (shimmerSum / (validPeaks.size - 1)) / meanAmp else 0f

        // 4. Harmonics-to-Noise Ratio (HNR) Approximation
        val voicedRatio = pitchTrack.size.toFloat() / numFrames.coerceAtLeast(1)
        val hnrDb = (10.0f * log10(voicedRatio.coerceAtLeast(1e-4f) / (1.0f - voicedRatio).coerceAtLeast(1e-4f))).coerceIn(-20f, 35f)

        // 5. Neural TTS Anomaly Scoring
        // Neural vocoders (HiFi-GAN, WaveGlow, ElevenLabs) generate overly flat microvariations (jitter < 0.008, shimmer < 0.015)
        // or unnaturally invariant pitch contours (f0StdDev < 8.0Hz in emotional context).
        var anomalyScore = 0.0f
        if (voicedRatio > 0.3f && jitterLocal < 0.007f) anomalyScore += 0.35f
        if (voicedRatio > 0.3f && shimmerLocal < 0.018f) anomalyScore += 0.25f
        if (f0StdDev in 0.0f..10.0f && pitchTrack.size > 15) anomalyScore += 0.25f
        if (hnrDb > 25.0f) anomalyScore += 0.15f

        val finalScore = anomalyScore.coerceIn(0f, 1f)

        Log.d(TAG, "Prosody: meanF0=%.1fHz, stdDev=%.1fHz, jitter=%.4f, shimmer=%.4f, TTS_Anomaly=%.2f"
            .format(meanF0, f0StdDev, jitterLocal, shimmerLocal, finalScore))

        return ProsodyMetrics(
            meanF0 = meanF0,
            f0StdDev = f0StdDev,
            jitterLocal = jitterLocal,
            shimmerLocal = shimmerLocal,
            hnrDb = hnrDb,
            ttsAnomalyScore = finalScore
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
