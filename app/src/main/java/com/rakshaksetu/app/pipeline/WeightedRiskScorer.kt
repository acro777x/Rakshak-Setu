package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStreamReader

/**
 * Multi-signal risk fusion scorer.
 * Combines semantic phrase similarity, voice clone probability, intent threat score,
 * loudness intensity, and speech rate anomalies into an aggregated continuous risk metric.
 */
class WeightedRiskScorer(private val context: Context) {
    private var wSimilarity: Float = 0.40f
    private var wDeepfake: Float = 0.30f
    private var wIntent: Float = 0.20f
    private var wLoudness: Float = 0.10f
    private var threshold: Float = 0.70f

    init {
        loadPolicy()
    }

    private fun loadPolicy() {
        try {
            val inputStream = context.assets.open("rl_policy.json")
            val jsonString = InputStreamReader(inputStream).readText()
            val json = JSONObject(jsonString)

            wSimilarity = json.optDouble("w_similarity", 0.40).toFloat()
            wDeepfake = json.optDouble("w_deepfake", 0.30).toFloat()
            wIntent = json.optDouble("w_intent", 0.20).toFloat()
            wLoudness = json.optDouble("w_stress", 0.10).toFloat()
            threshold = json.optDouble("threshold", 0.70).toFloat()

            // The shipped rl_policy.json declared a w_acoustic weight that nothing read,
            // and had no w_intent key at all, so the intent channel silently fell back to
            // its hardcoded default and the four live weights summed to 0.954 rather than
            // the calibrated 1.000 -- meaning `threshold` was applied to a differently
            // scaled score than the one the policy was trained against.
            //
            // Normalise here so the shipped threshold stays meaningful even if the
            // policy file is missing a channel or carries stale weights.
            val sum = wSimilarity + wDeepfake + wIntent + wLoudness
            if (sum > 0f && kotlin.math.abs(sum - 1.0f) > 0.01f) {
                Log.w(
                    "WeightedRiskScorer",
                    "rl_policy.json weights sum to $sum (expected ~1.0); normalising."
                )
                wSimilarity /= sum
                wDeepfake /= sum
                wIntent /= sum
                wLoudness /= sum
            }

            Log.i(
                "WeightedRiskScorer",
                "Risk Policy loaded (sim=%.3f, deepfake=%.3f, intent=%.3f, loud=%.3f, thr=%.2f, sum=%.3f)".format(
                    wSimilarity, wDeepfake, wIntent, wLoudness, threshold, sum
                )
            )
        } catch (e: Exception) {
            Log.e("WeightedRiskScorer", "Failed to load policy json. Using defaults.", e)
        }
    }

    /**
     * Continuous risk score in [0,1] from all pipeline evidence channels.
     */
    fun score(
        avgSimilarity: Float,
        cloneProb: Float,
        intentThreatScore: Float,
        maxLoudness: Float
    ): Float {
        return (avgSimilarity.coerceIn(0f, 1f) * wSimilarity) +
            (cloneProb.coerceIn(0f, 1f) * wDeepfake) +
            (intentThreatScore.coerceIn(0f, 1f) * wIntent) +
            (maxLoudness.coerceIn(0f, 1f) * wLoudness)
    }

    /**
     * Conviction decision against calibrated threshold.
     */
    fun evaluate(
        avgSimilarity: Float,
        cloneProb: Float,
        intentThreatScore: Float,
        maxLoudness: Float
    ): Boolean {
        val riskScore = score(avgSimilarity, cloneProb, intentThreatScore, maxLoudness)
        Log.d("WeightedRiskScorer", "Risk Score: $riskScore | Threshold: $threshold")
        return riskScore > threshold
    }
}
