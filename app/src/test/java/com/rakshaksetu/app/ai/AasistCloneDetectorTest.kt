package com.rakshaksetu.app.ai

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Unit tests verifying AasistCloneDetector DSP heuristics, window processing,
 * and boundary safety when operating without native ONNX runtime context.
 */
class AasistCloneDetectorTest {

    @Test
    fun analyzePcmWindow_emptyBytes_returnsBaselineSafeScores() {
        val detector = AasistCloneDetector(context = null)
        val result = detector.analyzePcmWindow(ByteArray(0))

        assertNotNull(result)
        assertTrue("Spoof score should be bounded in [0.0, 1.0]", result.spoofScore in 0.0f..1.0f)
        assertFalse("Neural model should not be used when context is null", result.isNeuralModelUsed)
        detector.release()
    }

    @Test
    fun analyzePcmWindow_silenceBuffer_returnsLowSpoofScore() {
        val detector = AasistCloneDetector(context = null)
        // 3 seconds of 16kHz mono 16-bit silence (48,000 samples = 96,000 bytes)
        val silencePcm = ByteArray(AasistCloneDetector.WINDOW_BYTES)

        val result = detector.analyzePcmWindow(silencePcm)
        assertNotNull(result)
        assertTrue("Silent audio should yield low phase discontinuity", result.phaseDiscontinuityIndex <= 0.20f)
        assertTrue("Silent audio formant jitter should be baseline", result.formantJitter <= 0.10f)
        assertTrue("Silence spoof score should be low", result.spoofScore < 0.50f)
        detector.release()
    }

    @Test
    fun analyzePcmWindow_naturalSpeechSineWave_producesBoundedMetrics() {
        val detector = AasistCloneDetector(context = null)
        val numSamples = AasistCloneDetector.WINDOW_SAMPLES
        val pcm = ByteArray(numSamples * 2)
        val buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)

        // Generate audio with fundamental 200Hz + variation (vocal harmonics)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / AasistCloneDetector.SAMPLE_RATE
            val sampleVal = (Math.sin(2.0 * Math.PI * 200.0 * t) * 10000 +
                             Math.sin(2.0 * Math.PI * 400.0 * t) * 4000).toInt().toShort()
            buffer.putShort(sampleVal)
        }

        val result = detector.analyzePcmWindow(pcm)
        assertNotNull(result)
        assertTrue(result.spoofScore in 0.0f..1.0f)
        assertTrue(result.phaseDiscontinuityIndex in 0.0f..1.0f)
        assertTrue(result.formantJitter in 0.0f..1.0f)
        assertTrue(result.highFreqHarmonicRatio in 0.0f..1.0f)
        detector.release()
    }

    @Test
    fun release_multipleCalls_isSafeAndIdempotent() {
        val detector = AasistCloneDetector(context = null)
        detector.release()
        detector.release() // Should not throw
    }
}
