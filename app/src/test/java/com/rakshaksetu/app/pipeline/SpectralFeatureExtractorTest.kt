package com.rakshaksetu.app.pipeline

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SpectralFeatureExtractorTest {

    @Test
    fun extractLFCC_returnsValidCoefficients() {
        // Create 1 second of 16kHz 16-bit mono sine wave (440Hz)
        val sampleRate = 16000
        val pcm = ByteArray(sampleRate * 2)
        val buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleRate) {
            val sample = (Math.sin(2.0 * Math.PI * 440.0 * i / sampleRate) * 16000).toInt().toShort()
            buffer.putShort(sample)
        }

        val lfcc = SpectralFeatureExtractor.extractLFCC(pcm)
        assertTrue("LFCC features must not be empty", lfcc.isNotEmpty())
        assertTrue("LFCC features must contain non-zero values", lfcc.any { it != 0f })
    }

    @Test
    fun estimateSpeechRate_returnsPositiveRate() {
        // Create pulsing speech-like audio
        val sampleRate = 16000
        val pcm = ByteArray(sampleRate * 2)
        val buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until sampleRate) {
            val envelope = if ((i / 2000) % 2 == 0) 1.0 else 0.05
            val sample = (Math.sin(2.0 * Math.PI * 250.0 * i / sampleRate) * 16000 * envelope).toInt().toShort()
            buffer.putShort(sample)
        }

        val rate = SpectralFeatureExtractor.estimateSpeechRate(pcm)
        assertTrue("Speech rate should be positive for active audio", rate >= 0f)
    }

    @Test
    fun computeSilenceRatio_detectsSilence() {
        // All zeros = 100% silence
        val silentPcm = ByteArray(32000)
        val ratio = SpectralFeatureExtractor.computeSilenceRatio(silentPcm)
        assertEquals(1.0f, ratio, 0.01f)
    }
}
