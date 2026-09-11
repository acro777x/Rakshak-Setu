package com.rakshaksetu.voip.ai

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.Locale

/**
 * High-performance scam phrase matcher implementing a Trie & Regex index
 * covering 450+ Indian cyber scam patterns (Digital Arrest, CBI/Police,
 * AI Voice Kidnapping, Screen Sharing, KYC Expiry, Parcel Narcotics).
 *
 * Emits highlighted character spans for the live streaming transcript HUD.
 */
class ScamPhraseTrie {

    companion object {
        private const val TAG = "ScamPhraseTrie"

        // Built-in core emergency patterns (precompiled regexes for <1ms matching)
        private val CORE_SCAM_PATTERNS = listOf(
            Regex("(?i)\\b(cbi|police|crime branch|enforcement directorate|ed officer|cyber cell)\\b.*\\b(warrant|arrest|money laundering|fir)\\b"),
            Regex("(?i)\\b(digital arrest|skype call|video call disconnect mat karna|room lock karo)\\b"),
            Regex("(?i)\\b(papa mujhe bachao|mummy mujhe kidnap|police ne pakad liya|accident ho gaya|rape case|bail)\\b"),
            Regex("(?i)\\b(anydesk|teamviewer|rustdesk|quicksupport)\\b.*\\b(download|install|code batao|allow access)\\b"),
            Regex("(?i)\\b(bank account block|kyc expired|pan link|otp share|card block)\\b.*\\b(verify|update|charges|penalty)\\b"),
            Regex("(?i)\\b(customs department|narcotics|mdma|drugs in parcel|fedex parcel|clearance fee)\\b"),
            Regex("(?i)\\b(electricity power cutoff|bijli cut|bill unpaid|disconnection tonight)\\b.*\\b(call immediately|officer number)\\b"),
            Regex("(?i)\\b(lottery won|part time job|telegram task|rating task|crypto deposit)\\b.*\\b(send money|processing fee)\\b"),
            Regex("(?i)(सीबीआई|डिजिटल अरेस्ट|वारंट|अरेस्ट वारंट|पुलिस अधिकारी|मनी लॉन्ड्रिंग|खाता फ्रीज)"),
            Regex("(?i)(पापा मुझे बचाओ|किडनैप|एक्सीडेंट|तुरंत पैसे भेजो|हॉस्पिटल में भर्ती)"),
            Regex("(?i)(एनीडेस्क|टीमव्यूअर|ओटीपी|केवाईसी एक्सपायर|बिजली कट)")
        )
    }

    data class MatchedSpan(
        val start: Int,
        val end: Int,
        val text: String,
        val category: String,
        val severity: Float
    )

    data class ScanResult(
        val isThreat: Boolean,
        val riskScore: Float,
        val topCategory: String,
        val matchedSpans: List<MatchedSpan>,
        val matchedPhrases: List<String>
    )

    // Trie Node for token-based fast prefix/phrase matching
    private class TrieNode {
        val children = mutableMapOf<String, TrieNode>()
        var isEndOfPhrase = false
        var phraseText: String? = null
        var category: String = "general"
        var weight: Float = 0.5f
    }

    private val root = TrieNode()
    private var totalPhrasesLoaded = 0

    /**
     * Insert a phrase into the Trie.
     */
    fun insert(phrase: String, category: String = "general", weight: Float = 0.7f) {
        val tokens = tokenize(phrase)
        if (tokens.isEmpty()) return

        var current = root
        for (token in tokens) {
            current = current.children.getOrPut(token) { TrieNode() }
        }
        current.isEndOfPhrase = true
        current.phraseText = phrase
        current.category = category
        current.weight = weight
        totalPhrasesLoaded++
    }

    /**
     * Load phrases from assets JSON (e.g. scam_phrases.json and attack_patterns.json).
     */
    fun loadFromAssets(context: Context) {
        // 1. Load scam_phrases.json
        try {
            context.assets.open("scam_phrases.json").use { stream ->
                val reader = InputStreamReader(stream)
                val type = object : TypeToken<Map<String, Any>>() {}.type
                val map: Map<String, Any> = Gson().fromJson(reader, type)
                val categories = map["categories"] as? List<Map<String, Any>>
                categories?.forEach { cat ->
                    val catId = cat["id"] as? String ?: "general"
                    val phrases = cat["phrases"] as? List<String> ?: emptyList()
                    val weight = when (catId) {
                        "digital_arrest", "ai_voice_kidnap" -> 0.95f
                        "screen_share_scam" -> 0.90f
                        "bank_kyc_fraud" -> 0.85f
                        else -> 0.75f
                    }
                    phrases.forEach { insert(it, catId, weight) }
                }
            }
            Log.i(TAG, "Loaded phrases from scam_phrases.json. Total Trie nodes: $totalPhrasesLoaded")
        } catch (e: Exception) {
            Log.w(TAG, "scam_phrases.json loading skipped: ${e.message}")
        }

        // 2. Load attack_patterns.json if present
        try {
            context.assets.open("attack_patterns.json").use { stream ->
                val reader = InputStreamReader(stream)
                val listType = object : TypeToken<List<Map<String, String>>>() {}.type
                val list: List<Map<String, String>> = Gson().fromJson(reader, listType)
                list.take(500).forEach { item ->
                    val text = item["text"]
                    val label = item["label"] ?: "attack_pattern"
                    if (!text.isNullOrBlank()) {
                        insert(text, label, 0.80f)
                    }
                }
            }
            Log.i(TAG, "Loaded attack_patterns.json. Total indexed: $totalPhrasesLoaded")
        } catch (e: Exception) {
            Log.w(TAG, "attack_patterns.json loading skipped: ${e.message}")
        }

        // 3. Seed default keywords if assets not available
        if (totalPhrasesLoaded == 0) {
            seedDefaults()
        }
    }

    /**
     * Seeds fallback keywords for offline/standalone execution.
     */
    fun seedDefaults() {
        val defaults = listOf(
            "cbi officer" to "digital_arrest",
            "digital arrest" to "digital_arrest",
            "arrest warrant issued" to "digital_arrest",
            "video call disconnect mat karna" to "digital_arrest",
            "money laundering case" to "digital_arrest",
            "police crime branch" to "digital_arrest",
            "papa mujhe bachao" to "ai_voice_kidnap",
            "mummy mujhe kidnap kar liya" to "ai_voice_kidnap",
            "police ne pakad liya rape case" to "ai_voice_kidnap",
            "urgent hospital ransom" to "ai_voice_kidnap",
            "anydesk app download karo" to "screen_share_scam",
            "teamviewer install karo" to "screen_share_scam",
            "9 digit code batao" to "screen_share_scam",
            "bank account freeze ho jayega" to "bank_kyc_fraud",
            "kyc verification otp" to "bank_kyc_fraud",
            "electricity power cutoff tonight" to "electricity_scam",
            "parcel illegal drugs customs" to "customs_scam"
        )
        defaults.forEach { (phrase, cat) ->
            insert(phrase, cat, 0.90f)
        }
    }

    /**
     * Scan live streaming transcript for scam keywords and regex patterns.
     */
    fun scan(transcript: String): ScanResult {
        if (transcript.isBlank()) {
            return ScanResult(false, 0f, "safe", emptyList(), emptyList())
        }

        val spans = mutableListOf<MatchedSpan>()
        val matchedPhrases = mutableListOf<String>()
        var maxRisk = 0.0f
        var dominantCategory = "safe"

        // 1. Regex scanning for fast complex intent detection
        for (pattern in CORE_SCAM_PATTERNS) {
            val match = pattern.find(transcript)
            if (match != null) {
                val span = MatchedSpan(
                    start = match.range.first,
                    end = match.range.last + 1,
                    text = match.value,
                    category = "severe_scam_pattern",
                    severity = 0.95f
                )
                spans.add(span)
                matchedPhrases.add(match.value)
                maxRisk = maxOf(maxRisk, 0.95f)
                dominantCategory = "digital_arrest_or_kidnap"
            }
        }

        // 2. Trie sliding window matching
        val lowerText = transcript.lowercase(Locale.ROOT)
        val tokens = tokenize(lowerText)
        for (i in tokens.indices) {
            var current = root
            for (j in i until minOf(i + 10, tokens.size)) {
                val token = tokens[j]
                current = current.children[token] ?: break
                if (current.isEndOfPhrase && current.phraseText != null) {
                    val phrase = current.phraseText!!
                    val idx = lowerText.indexOf(phrase)
                    val start = if (idx >= 0) idx else 0
                    val end = minOf(start + phrase.length, transcript.length)

                    spans.add(
                        MatchedSpan(
                            start = start,
                            end = end,
                            text = phrase,
                            category = current.category,
                            severity = current.weight
                        )
                    )
                    matchedPhrases.add(phrase)
                    if (current.weight > maxRisk) {
                        maxRisk = current.weight
                        dominantCategory = current.category
                    }
                }
            }
        }

        val isThreat = maxRisk >= 0.70f || spans.isNotEmpty()
        return ScanResult(
            isThreat = isThreat,
            riskScore = maxRisk,
            topCategory = dominantCategory,
            matchedSpans = spans.distinctBy { "${it.start}_${it.end}" },
            matchedPhrases = matchedPhrases.distinct()
        )
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 2 }
    }
}
