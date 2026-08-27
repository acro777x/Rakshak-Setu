package com.rakshaksetu.app.pipeline

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class VocalStressDetectorTest {

    @Test
    fun evaluateStress_highFrequencyNoise_detectsStress() {
        val sampleRate = 16000
        val durationSec = 0.5f
        val numSamples = (sampleRate * durationSec).toInt()
        val pcmData = ByteArray(numSamples * 2)

        // Generate 3000 Hz tone (high-frequency energy)
        for (i in 0 until numSamples) {
            val sample = (sin(2.0 * Math.PI * 3000.0 * i / sampleRate) * 16000.0).toInt().toShort()
            pcmData[i * 2] = (sample.toInt() and 0xFF).toByte()
            pcmData[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }

        val stress = VocalStressDetector.evaluateStress(pcmData)
        assertTrue("High freq energy ratio should be high", stress.highFreqRatio > 0.50f)
    }

    @Test
    fun evaluateStress_emptyData_returnsZero() {
        val stress = VocalStressDetector.evaluateStress(ByteArray(0))
        assertEquals(0f, stress.stressScore, 0.001f)
        assertFalse(stress.isAggressiveTone)
    }
}
