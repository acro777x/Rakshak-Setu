package com.rakshaksetu.app.pipeline

import org.junit.Assert.*
import org.junit.Test

class NeuralVocoderDetectorTest {

    @Test
    fun analyzeVocoderArtifacts_syntheticPeriodicWave_detectsSubscalePattern() {
        val sampleRate = 16000
        val durationSec = 1.0f
        val numSamples = (sampleRate * durationSec).toInt()
        val pcmData = ByteArray(numSamples * 2)

        // Generate synthetic signal with step-wise periodic 16-sample subscale pulses
        for (i in 0 until numSamples) {
            val isPulse = (i % 16 == 0)
            val sample = if (isPulse) 15000.toShort() else (Math.sin(i * 0.05) * 2000).toInt().toShort()
            pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val result = NeuralVocoderDetector.analyzeVocoderArtifacts(pcmData)
        assertTrue("Should detect subscale periodic artifact", result.subscaleLagScore > 0.30f)
    }

    @Test
    fun analyzeVocoderArtifacts_emptyPcm_returnsZero() {
        val result = NeuralVocoderDetector.analyzeVocoderArtifacts(ByteArray(0))
        assertEquals(0f, result.neuralVocoderConfidence, 0.001f)
    }
}
