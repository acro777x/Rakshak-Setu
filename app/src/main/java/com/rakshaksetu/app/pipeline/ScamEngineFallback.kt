package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.InputStreamReader
import kotlin.math.exp

/**
 * 3-Tier Multi-Layer Fallback & Safety Scam Engine
 * Tier 1: Deep Neural ONNX Classifier (High-capacity deep learning)
 * Tier 2: On-Device Statistical NLP (TF-IDF + Logistic Regression + WordPiece Cosine Matching)
 * Tier 3: Zero-Failure Deterministic Rule & Regex Scanner (Instant offline safety net)
 */
object ScamEngineFallback {
    private const val TAG = "ScamEngineFallback"

    data class ScanVerdict(
        val isScam: Boolean,
        val confidence: Float,
        val category: String,
        val tierUsed: String
    )

    private var tfidfVocab: Map<String, Int>? = null
    private var tfidfIdf: FloatArray? = null
    private var tfidfWeights: FloatArray? = null
    private var tfidfIntercept: Float = 0.0f
    private var isTier2Loaded = false

    private val TIER3_KEYWORDS = mapOf(
        "digital_arrest" to listOf("cbi", "arrest", "warrant", "court", "police", "narcotics", "customs drugs", "digital arrest"),
        "kyc_fraud" to listOf("kyc", "pan card", "freeze", "block", "suspend", "debit card expire", "aadhar link"),
        "courier_customs" to listOf("fedex", "parcel", "customs", "illegal package", "contraband", "clearance fee"),
        "tech_support" to listOf("anydesk", "teamviewer", "quicksupport", "trojan", "virus detected", "remote access"),
        "lottery_refund" to listOf("lottery", "kbc", "prize", "cashback", "lucky draw", "tax fee", "pre-approved loan"),
        "family_emergency" to listOf("accident", "hospital", "police station", "emergency deposit", "bail money"),
        "job_task_scam" to listOf("work from home", "daily 5000", "telegram task", "youtube review", "like videos"),
        "upi_qr_scam" to listOf("qr code", "scan receive", "collect request", "advance payment", "upi pin daalo")
    )

    fun init(context: Context) {
        loadTier2Classifier(context)
    }

    private fun loadTier2Classifier(context: Context) {
        try {
            context.assets.open("scam_classifier_config.json").use { inputStream ->
                val reader = InputStreamReader(inputStream)
                val json = JSONObject(reader.readText())
                val vocabObj = json.getJSONObject("vocabulary")
                val vocabMap = mutableMapOf<String, Int>()
                val keys = vocabObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    vocabMap[k] = vocabObj.getInt(k)
                }
                tfidfVocab = vocabMap

                val idfArr = json.getJSONArray("idf")
                tfidfIdf = FloatArray(idfArr.length()) { idfArr.getDouble(it).toFloat() }

                val weightsArr = json.getJSONArray("model_weights")
                tfidfWeights = FloatArray(weightsArr.length()) { weightsArr.getDouble(it).toFloat() }

                tfidfIntercept = json.optDouble("model_intercept", 0.0).toFloat()
                isTier2Loaded = true
                Log.i(TAG, "Tier 2 Statistical NLP Classifier loaded successfully (${vocabMap.size} features).")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Tier 2 classifier not available, Tier 3 active.", e)
        }
    }

    /**
     * Evaluates a transcript through the 3-tier fallback hierarchy.
     */
    fun evaluate(transcript: String): ScanVerdict {
        val cleanText = transcript.trim()
        if (cleanText.isEmpty()) {
            return ScanVerdict(isScam = false, confidence = 0.0f, category = "benign", tierUsed = "EMPTY_INPUT")
        }

        // --- TIER 1: Deep Neural Match ---
        try {
            val (embeddingSim, embeddingCat) = EmbeddingEngine.findBestMatch(cleanText, threshold = 0.82f)
            if (embeddingCat != null && embeddingSim >= 0.82f) {
                return ScanVerdict(
                    isScam = true,
                    confidence = embeddingSim,
                    category = embeddingCat,
                    tierUsed = "TIER_1_DEEP_NEURAL"
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Tier 1 evaluation skipped: ${e.message}")
        }

        // --- TIER 2: Statistical NLP (TF-IDF + Logistic Regression) ---
        if (isTier2Loaded && tfidfVocab != null && tfidfWeights != null) {
            try {
                val words = cleanText.lowercase().split("\\s+".toRegex())
                val vocab = tfidfVocab!!
                val weights = tfidfWeights!!
                val idf = tfidfIdf

                var logit = tfidfIntercept
                for (w in words) {
                    val idx = vocab[w]
                    if (idx != null && idx < weights.size) {
                        val idfWeight = if (idf != null && idx < idf.size) idf[idx] else 1.0f
                        logit += weights[idx] * idfWeight
                    }
                }

                val prob = (1.0 / (1.0 + exp(-logit.toDouble()))).toFloat()
                if (prob > 0.65f) {
                    val cat = detectCategoryFromKeywords(cleanText)
                    return ScanVerdict(
                        isScam = true,
                        confidence = prob,
                        category = cat,
                        tierUsed = "TIER_2_STATISTICAL_NLP"
                    )
                }
            } catch (e: Exception) {
                Log.d(TAG, "Tier 2 evaluation skipped: ${e.message}")
            }
        }

        // --- TIER 3: Zero-Failure Hard-Safety Regex & Rule Engine ---
        val lower = cleanText.lowercase()
        var matchCount = 0
        var matchedCat = "unknown_threat"

        for ((cat, keywords) in TIER3_KEYWORDS) {
            for (kw in keywords) {
                if (lower.contains(kw)) {
                    matchCount++
                    matchedCat = cat
                }
            }
        }

        if (matchCount >= 2) {
            val conf = (0.70f + (matchCount * 0.08f)).coerceAtMost(0.98f)
            return ScanVerdict(
                isScam = true,
                confidence = conf,
                category = matchedCat,
                tierUsed = "TIER_3_SAFETY_REGEX"
            )
        }

        return ScanVerdict(
            isScam = false,
            confidence = 0.10f,
            category = "benign",
            tierUsed = "TIER_3_BENIGN"
        )
    }

    private fun detectCategoryFromKeywords(text: String): String {
        val lower = text.lowercase()
        for ((cat, keywords) in TIER3_KEYWORDS) {
            if (keywords.any { lower.contains(it) }) {
                return cat
            }
        }
        return "general_fraud"
    }
}
