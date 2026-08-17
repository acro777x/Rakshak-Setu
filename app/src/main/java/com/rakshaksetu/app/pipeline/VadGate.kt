package com.rakshaksetu.app.pipeline

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VadGate {
    // Tunable thresholds for silence detection (could be moved to Remote Config)
    private const val RMS_THRESHOLD = 50.0
    private const val ZCR_THRESHOLD = 0.05 // minimal zero crossing rate to be considered speech

    /**
     * Determines if a 5-second audio segment contains speech (not just silence/noise).
     * @param pcmData 16-bit PCM mono byte array.
     * @return true if the segment has voice activity, false if it is considered EMPTY.
     */
    fun isVoiceActive(pcmData: ByteArray): Boolean {
        if (pcmData.isEmpty()) return false

        val shorts = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val numSamples = shorts.capacity()
        if (numSamples == 0) return false

        var sumSquares = 0.0
        var zeroCrossings = 0
        var prevSample = shorts.get(0).toInt()

        for (i in 0 until numSamples) {
            val sample = shorts.get(i).toInt()
            
            // RMS accumulation
            sumSquares += (sample * sample)
            
            // Zero-crossing accumulation
            if ((sample >= 0 && prevSample < 0) || (sample < 0 && prevSample >= 0)) {
                zeroCrossings++
            }
            prevSample = sample
        }

        val rms = sqrt(sumSquares / numSamples)
        val zcr = zeroCrossings.toDouble() / numSamples

        // For a segment to be active, it should have energy above the noise floor
        // and a reasonable zero crossing rate indicative of human speech (not just a flat hum).
        return rms >= RMS_THRESHOLD // we can add && zcr >= ZCR_THRESHOLD if needed, but RMS is primary
    }
}
