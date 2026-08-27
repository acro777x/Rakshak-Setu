package com.rakshaksetu.app.pipeline

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class ProsodyAnalyzerTest {

    @Test
    fun analyze_syntheticSineWave_detectsLowJitterAndShimmer() {
        val sampleRate = 16000
        val durationSec = 1.0f
        val numSamples = (sampleRate * durationSec).toInt()
        val pcmData = ByteArray(numSamples * 2)

        // Generate pure 200 Hz sine wave (zero natural human jitter/shimmer)
        for (i in 0 until numSamples) {
            val sample = (sin(2.0 * Math.PI * 200.0 * i / sampleRate) * 16000.0).toInt().toShort()
            pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val metrics = ProsodyAnalyzer.analyze(pcmData)
        assertTrue("Sine wave should have low jitter", metrics.jitterLocal < 0.01f)
        assertTrue("Sine wave should have low shimmer", metrics.shimmerLocal < 0.02f)
        assertTrue("Pure sine wave should show high TTS anomaly score", metrics.ttsAnomalyScore >= 0.50f)
    }

    @Test
    fun analyze_emptyPcm_returnsZeroMetrics() {
        val metrics = ProsodyAnalyzer.analyze(ByteArray(0))
        assertEquals(0f, metrics.ttsAnomalyScore, 0.001f)
    }
}
