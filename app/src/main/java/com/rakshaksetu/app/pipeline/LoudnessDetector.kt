package com.rakshaksetu.app.pipeline

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Loudness and energy detector for audio segments.
 * Computes RMS energy of PCM audio to identify elevated speech volume,
 * shouting, or aggressive tone without making false AI emotion claims.
 */
object LoudnessDetector {
    private const val TAG = "LoudnessDetector"
    private const val LOUD_RMS_THRESHOLD = 2000.0

    /**
     * @param pcmData 16-bit PCM audio segment
     * @return Loudness intensity score from 0.0 (quiet/normal) to 1.0 (very loud/shouting)
     */
    fun analyzeLoudness(pcmData: ByteArray): Float {
        if (pcmData.isEmpty()) return 0.0f

        val shorts = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSquares = 0.0

        for (i in 0 until shorts.capacity()) {
            val sample = shorts.get(i).toInt()
            sumSquares += (sample * sample)
        }

        val rms = sqrt(sumSquares / shorts.capacity())
        Log.d(TAG, "Audio RMS Energy: $rms")

        return (rms / LOUD_RMS_THRESHOLD).coerceIn(0.0, 1.0).toFloat()
    }
}
