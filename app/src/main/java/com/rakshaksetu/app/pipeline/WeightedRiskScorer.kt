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

    enum class RiskScenario {
        STANDARD,
        HIGH_VALUE_TRANSACTION,
        CXO_PRIVILEGED_APPROVAL
    }

    private var wSimilarity: Float = 0.35f
    private var wDeepfake: Float = 0.25f
    private var wVocoder: Float = 0.15f
    private var wIntent: Float = 0.15f
    private var wStress: Float = 0.10f
    private var threshold: Float = 0.70f

    init {
        loadPolicy()
    }

    private fun loadPolicy() {
        try {
            val inputStream = context.assets.open("rl_policy.json")
            val jsonString = InputStreamReader(inputStream).readText()
            val json = JSONObject(jsonString)

            wSimilarity = json.optDouble("w_similarity", 0.35).toFloat()
            wDeepfake = json.optDouble("w_deepfake", 0.25).toFloat()
            wVocoder = json.optDouble("w_vocoder", 0.15).toFloat()
            wIntent = json.optDouble("w_intent", 0.15).toFloat()
            wStress = json.optDouble("w_stress", 0.10).toFloat()
            threshold = json.optDouble("threshold", 0.70).toFloat()

            Log.i(
                "WeightedRiskScorer",
                "Risk Policy loaded (sim=%.2f, deepfake=%.2f, vocoder=%.2f, intent=%.2f, stress=%.2f, thr=%.2f)".format(
                    wSimilarity, wDeepfake, wVocoder, wIntent, wStress, threshold
                )
            )
        } catch (e: Exception) {
            Log.e("WeightedRiskScorer", "Failed to load policy json. Using defaults.", e)
        }
    }

    /**
     * Continuous composite risk score in [0,1] from all pipeline evidence channels with scenario adaptation.
     */
    fun score(
        avgSimilarity: Float,
        cloneProb: Float,
        intentThreatScore: Float,
        maxLoudness: Float,
        vocoderConfidence: Float = 0f,
        scenario: RiskScenario = RiskScenario.STANDARD,
        reputationDelta: Float = 0f
    ): Float {
        val (wClone, wVoc, wSim, wInt, wStr) = when (scenario) {
            RiskScenario.CXO_PRIVILEGED_APPROVAL -> listOf(0.45f, 0.25f, 0.15f, 0.10f, 0.05f)
            RiskScenario.HIGH_VALUE_TRANSACTION -> listOf(0.35f, 0.20f, 0.25f, 0.15f, 0.05f)
            RiskScenario.STANDARD -> listOf(wDeepfake, wVocoder, wSimilarity, wIntent, wStress)
        }

        val rawScore = (cloneProb.coerceIn(0f, 1f) * wClone) +
            (vocoderConfidence.coerceIn(0f, 1f) * wVoc) +
            (avgSimilarity.coerceIn(0f, 1f) * wSim) +
            (intentThreatScore.coerceIn(0f, 1f) * wInt) +
            (maxLoudness.coerceIn(0f, 1f) * wStr) +
            reputationDelta

        return rawScore.coerceIn(0f, 1f)
    }

    /**
     * Conviction decision against calibrated threshold.
     */
    fun evaluate(
        avgSimilarity: Float,
        cloneProb: Float,
        intentThreatScore: Float,
        maxLoudness: Float,
        vocoderConfidence: Float = 0f,
        scenario: RiskScenario = RiskScenario.STANDARD,
        reputationDelta: Float = 0f
    ): Boolean {
        val effectiveThreshold = when (scenario) {
            RiskScenario.CXO_PRIVILEGED_APPROVAL -> 0.45f
            RiskScenario.HIGH_VALUE_TRANSACTION -> 0.50f
            RiskScenario.STANDARD -> threshold
        }
        val riskScore = score(avgSimilarity, cloneProb, intentThreatScore, maxLoudness, vocoderConfidence, scenario, reputationDelta)
        Log.d("WeightedRiskScorer", "Risk Score: $riskScore | Effective Threshold: $effectiveThreshold (Scenario: $scenario)")
        return riskScore >= effectiveThreshold
    }
}
