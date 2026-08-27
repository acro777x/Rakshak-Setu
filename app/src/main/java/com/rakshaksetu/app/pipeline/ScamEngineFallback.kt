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
        "digital_arrest" to listOf("cbi", "arrest", "warrant", "court", "police", "narcotics", "customs drugs", "digital arrest", "गिरफ्तार", "वारंट", "पुलिस", "कोर्ट", "नार्कोटिक्स"),
        "kyc_fraud" to listOf("kyc", "pan card", "freeze", "block", "suspend", "debit card expire", "aadhar link", "आधार", "पैन कार्ड", "केवाईसी", "ब्लॉक"),
        "courier_customs" to listOf("fedex", "parcel", "customs", "illegal package", "contraband", "clearance fee", "पार्सल", "कस्टम", "कूरियर"),
        "screen_share_scam" to listOf("anydesk", "teamviewer", "quicksupport", "trojan", "virus detected", "remote access", "screen share", "एनीडेस्क", "रिमोट एक्सेस", "स्क्रीन शेयर"),
        "loan_lottery" to listOf("lottery", "kbc", "prize", "cashback", "lucky draw", "tax fee", "pre-approved loan", "लॉटरी", "केबीसी", "इनाम", "लकी ड्रॉ", "बधाई हो", "जीती है", "प्राइज"),
        "ai_voice_kidnap" to listOf("accident", "hospital", "police station", "emergency deposit", "bail money", "kidnap", "ransom", "एक्सीडेंट", "हॉस्पिटल", "जमानत", "अपहरण"),
        "job_task_scam" to listOf("work from home", "daily 5000", "telegram task", "youtube review", "like videos", "घर बैठे", "रोजाना", "टेलीग्राम"),
        "upi_qr_scam" to listOf("qr code", "scan receive", "collect request", "advance payment", "upi pin daalo", "क्यूआर कोड", "यूपीआई पिन"),
        "sextortion_blackmail" to listOf("video call record", "viral", "nude", "blackmail", "whatsapp contacts", "वीडियो कॉल", "वायरल", "ब्लैकमेल"),
        "crypto_investment" to listOf("crypto", "bitcoin", "stock market", "guaranteed profit", "vip telegram", "शेयर मार्केट", "क्रिप्टो"),
        "traffic_challan" to listOf("traffic challan", "e-challan", "rto", "vehicle impound", "traffic fine", "ट्रैफिक चालान", "ई-चालान", "गाड़ी जब्त"),
        "pension_epfo" to listOf("pension", "epfo", "life certificate", "pf withdrawal", "पेंशन", "ईपीएफओ", "जीवन प्रमाण"),
        "trai_sim_block" to listOf("trai", "sim block", "telecom", "disconnect", "ट्राई", "सिम ब्लॉक", "दूरसंचार"),
        "electricity_bill" to listOf("electricity bill", "power cut", "meter", "bijli", "बिजली बिल", "बिजली कट")
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
