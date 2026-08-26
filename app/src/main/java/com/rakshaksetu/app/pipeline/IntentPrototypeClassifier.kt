package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import org.json.JSONObject
import kotlin.math.sqrt

/**
 * Intent-based scam detection using MiniLM prototype matching.
 * Zero extra model download — reuses the existing MiniLM ONNX encoder.
 *
 * Instead of matching transcripts against exact scam phrases (bypassable),
 * matches against behavioral INTENT prototypes:
 * - pressure_urgency
 * - authority_impersonation
 * - financial_extraction
 * - isolation_tactic
 * - remote_access
 * - benign_conversation (negative class)
 *
 * Catches unknown/novel scams by detecting behavioral patterns, not scripts.
 * Resistant to APK decompilation — intent descriptions are abstract, not
 * specific evasible phrases.
 */
class IntentPrototypeClassifier(private val context: Context) {
    companion object {
        private const val TAG = "IntentClassifier"
        private const val INTENT_THRESHOLD = 0.55f
        private const val BENIGN_LABEL = "benign_conversation"
    }

    data class IntentScore(
        val intentId: String,
        val label: String,
        val score: Float
    )

    data class IntentResult(
        val topIntent: IntentScore,
        val allScores: Map<String, Float>,
        val threatIntentCount: Int,
        val isBenign: Boolean
    )

    // Pre-computed embeddings for each intent's prototypes
    private val intentEmbeddings = mutableMapOf<String, List<FloatArray>>()
    private val intentLabels = mutableMapOf<String, String>()
    private var isInitialized = false

    /**
     * Load intent prototypes from JSON and pre-compute their MiniLM embeddings.
     * Call AFTER EmbeddingEngine is initialized with the ONNX model.
     */
    @Synchronized
    fun init() {
        if (isInitialized) return
        if (!EmbeddingEngine.isSemanticMode()) {
            Log.w(TAG, "MiniLM not ready — intent classification deferred")
            return
        }

        try {
            val json = context.assets.open("intent_prototypes.json")
                .bufferedReader().readText()
            val root = JSONObject(json)
            val intents = root.getJSONObject("intents")

            for (intentId in intents.keys()) {
                val intentObj = intents.getJSONObject(intentId)
                intentLabels[intentId] = intentObj.getString("label")

                val prototypes = intentObj.getJSONArray("prototypes")
                val embeddings = mutableListOf<FloatArray>()
                for (i in 0 until prototypes.length()) {
                    val protoText = prototypes.getString(i)
                    val embedding = EmbeddingEngine.generateEmbedding(protoText)
                    embeddings.add(embedding)
                }
                intentEmbeddings[intentId] = embeddings

                Log.d(TAG, "Loaded intent '$intentId': ${embeddings.size} prototypes")
            }

            isInitialized = true
            Log.i(TAG, "Intent classifier ready: ${intentEmbeddings.size} intents, " +
                "${intentEmbeddings.values.sumOf { it.size }} total prototypes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize intent classifier", e)
        }
    }

    fun isReady(): Boolean = isInitialized

    /**
     * Classify a transcript segment against all intent prototypes.
     * Returns the top matching intent, all scores, and threat count.
     */
    fun classify(transcript: String): IntentResult {
        if (!isInitialized || transcript.isBlank()) {
            return IntentResult(
                topIntent = IntentScore("unknown", "Unknown", 0f),
                allScores = emptyMap(),
                threatIntentCount = 0,
                isBenign = true
            )
        }

        val segmentEmbedding = EmbeddingEngine.generateEmbedding(transcript)
        val scores = mutableMapOf<String, Float>()

        for ((intentId, protoEmbeddings) in intentEmbeddings) {
            // Max similarity across all prototypes for this intent
            val maxSim = protoEmbeddings.maxOfOrNull { proto ->
                cosineSimilarity(segmentEmbedding, proto)
            } ?: 0f
            scores[intentId] = maxSim
        }

        val topEntry = scores.maxByOrNull { it.value }
        val topIntent = IntentScore(
            intentId = topEntry?.key ?: "unknown",
            label = intentLabels[topEntry?.key] ?: "Unknown",
            score = topEntry?.value ?: 0f
        )

        // Count threat intents above threshold (exclude benign)
        val threatCount = scores.count { (id, score) ->
            id != BENIGN_LABEL && score > INTENT_THRESHOLD
        }

        val isBenign = topIntent.intentId == BENIGN_LABEL ||
            (threatCount == 0 && (scores[BENIGN_LABEL] ?: 0f) > INTENT_THRESHOLD)

        return IntentResult(
            topIntent = topIntent,
            allScores = scores,
            threatIntentCount = threatCount,
            isBenign = isBenign
        )
    }

    /**
     * Analyze all segments from a call and produce aggregate intent verdict.
     * ≥2 distinct threat intents across the call = SCAM conviction.
     */
    fun analyzeCall(transcripts: List<String>): CallIntentResult {
        if (transcripts.isEmpty()) {
            return CallIntentResult(false, emptyMap(), 0, emptyList())
        }

        val segmentResults = transcripts.map { classify(it) }
        val aggregatedScores = mutableMapOf<String, Float>()

        // Max score per intent across all segments
        for (result in segmentResults) {
            for ((intentId, score) in result.allScores) {
                aggregatedScores[intentId] = maxOf(
                    aggregatedScores.getOrDefault(intentId, 0f),
                    score
                )
            }
        }

        // Distinct threat intents that fired above threshold
        val firedThreats = aggregatedScores.filter { (id, score) ->
            id != BENIGN_LABEL && score > INTENT_THRESHOLD
        }

        val isScam = firedThreats.size >= 2
        val dominantThreat = firedThreats.maxByOrNull { it.value }

        return CallIntentResult(
            isScam = isScam,
            intentScores = aggregatedScores,
            distinctThreatCount = firedThreats.size,
            segmentResults = segmentResults,
            dominantThreat = dominantThreat?.key,
            dominantThreatScore = dominantThreat?.value ?: 0f
        )
    }

    data class CallIntentResult(
        val isScam: Boolean,
        val intentScores: Map<String, Float>,
        val distinctThreatCount: Int,
        val segmentResults: List<IntentResult>,
        val dominantThreat: String? = null,
        val dominantThreatScore: Float = 0f
    )

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0f; var n1 = 0f; var n2 = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            n1 += v1[i] * v1[i]
            n2 += v2[i] * v2[i]
        }
        if (n1 == 0f || n2 == 0f) return 0f
        return dot / (sqrt(n1) * sqrt(n2))
    }
}
