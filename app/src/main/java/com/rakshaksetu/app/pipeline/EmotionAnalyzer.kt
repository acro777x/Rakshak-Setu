package com.rakshaksetu.app.pipeline

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * AI-P2-03: Emotion / Stress Analysis
 * Analyzes pitch and energy variations to detect if the user is highly stressed or panicked.
 */
object EmotionAnalyzer {
    private const val TAG = "EmotionAnalyzer"
    private const val STRESS_RMS_THRESHOLD = 2000.0 // Very loud shouting/panic
    
    /**
     * @param pcmData 16-bit PCM audio segment
     * @return Stress level score from 0.0 (Calm) to 1.0 (Highly Panicked)
     */
    fun analyzeStress(pcmData: ByteArray): Float {
        if (pcmData.isEmpty()) return 0.0f
        
        val shorts = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSquares = 0.0
        
        for (i in 0 until shorts.capacity()) {
            val sample = shorts.get(i).toInt()
            sumSquares += (sample * sample)
        }
        
        val rms = sqrt(sumSquares / shorts.capacity())
        Log.d(TAG, "Emotion Analysis - Audio RMS: $rms")
        
        // Simple heuristic: Unusually high RMS energy indicates shouting/panic
        // Advanced version would compute pitch (F0) variance over time.
        val stressScore = (rms / STRESS_RMS_THRESHOLD).coerceIn(0.0, 1.0).toFloat()
        
        return stressScore
    }
}
