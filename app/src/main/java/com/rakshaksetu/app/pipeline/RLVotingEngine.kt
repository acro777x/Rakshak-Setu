package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * AI-P6-04: Reinforcement Learning (RL) Voting Engine
 * Replaces the static A4 Voting Engine with a dynamic policy trained via Python RL agent.
 */
class RLVotingEngine(private val context: Context) {
    private var wSimilarity: Float = 0.5f
    private var wDeepfake: Float = 0.2f
    private var wStress: Float = 0.1f
    private var wAcoustic: Float = 0.2f
    private var threshold: Float = 0.75f

    init {
        loadPolicy()
    }

    private fun loadPolicy() {
        try {
            val inputStream = context.assets.open("rl_policy.json")
            val jsonString = InputStreamReader(inputStream).readText()
            val json = JSONObject(jsonString)

            wSimilarity = json.getDouble("w_similarity").toFloat()
            wDeepfake = json.getDouble("w_deepfake").toFloat()
            wStress = json.getDouble("w_stress").toFloat()
            wAcoustic = json.getDouble("w_acoustic").toFloat()
            threshold = json.getDouble("threshold").toFloat()

            Log.i("RLVotingEngine", "Successfully loaded RL Policy. Weights updated dynamically.")
        } catch (e: Exception) {
            Log.e("RLVotingEngine", "Failed to load rl_policy.json. Using default fallback weights.", e)
        }
    }

    /**
     * Calculates final risk score based on RL learned weights.
     */
    fun evaluate(
        avgSimilarity: Float, 
        maxDeepfakeProb: Float, 
        maxStressScore: Float, 
        hasCallCenterNoise: Boolean
    ): Boolean {
        val acousticVal = if (hasCallCenterNoise) 1.0f else 0.0f
        
        val riskScore = (avgSimilarity * wSimilarity) + 
                        (maxDeepfakeProb * wDeepfake) + 
                        (maxStressScore * wStress) + 
                        (acousticVal * wAcoustic)
        
        Log.d("RLVotingEngine", "Calculated Risk Score: $riskScore | Threshold: $threshold")
        return riskScore > threshold
    }
}
