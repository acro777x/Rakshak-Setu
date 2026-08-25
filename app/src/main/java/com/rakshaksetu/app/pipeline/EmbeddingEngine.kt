package com.rakshaksetu.app.pipeline

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlin.math.sqrt

/**
 * 384-dim MiniLM sentence embeddings via ONNX Runtime, with a calibrated lexical
 * matcher as the graceful-degradation path when the encoder model has not been
 * downloaded yet. The pipeline NEVER fabricates similarities: without the ONNX
 * session, matching falls through to LexicalScamMatcher over the phrase library.
 */
object EmbeddingEngine {
    private const val TAG = "EmbeddingEngine"
    private const val EMBED_DIM = 384

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: WordPieceTokenizer? = null
    private var sessionAvailable = false

    // Cache of embedded scam phrases
    private val phraseEmbeddings = mutableMapOf<String, FloatArray>()

    /** Legacy test/compat entry — delegates to [ensureInitialized]. */
    fun init(context: android.content.Context, modelPath: String) {
        ensureInitialized(context, modelPath)
    }

    /**
     * Idempotent initialization. Tokenizer always loads from assets; the ONNX session
     * only attaches when a valid encoder file exists at [modelPath] (runtime download).
     */
    @Synchronized
    fun ensureInitialized(context: android.content.Context, modelPath: String?) {
        if (tokenizer == null) {
            try {
                tokenizer = WordPieceTokenizer(context, "vocab.txt")
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load tokenizer", e)
            }
        }
        if (!sessionAvailable && modelPath != null) {
            val f = java.io.File(modelPath)
            if (f.exists() && f.length() > 1_000_000) {
                try {
                    ortEnv = OrtEnvironment.getEnvironment()
                    ortSession = ortEnv?.createSession(f.absolutePath, OrtSession.SessionOptions())
                    sessionAvailable = true
                    Log.i(TAG, "ONNX MiniLM encoder attached: $modelPath")
                    precomputeLibraryEmbeddings(ScamPhraseLibrary.getFlattenedPhrases())
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to initialize ONNX Runtime with $modelPath", e)
                    ortSession = null
                    sessionAvailable = false
                }
            } else {
                Log.d(TAG, "Encoder model not present yet — lexical matcher active.")
            }
        }
    }

    fun isSemanticMode(): Boolean = sessionAvailable

    /**
     * Embed the scam phrase library and cache the vectors (semantic mode only).
     */
    @Synchronized
    fun precomputeLibraryEmbeddings(phrases: List<Pair<String, ScamCategory>>) {
        if (!sessionAvailable) return
        phrases.forEach { (phrase, _) ->
            if (!phraseEmbeddings.containsKey(phrase)) {
                phraseEmbeddings[phrase] = generateEmbedding(phrase)
            }
        }
    }

    /**
     * Generates a 384-dimensional L2-normalized embedding for the given text.
     * Returns an all-zero vector when no encoder is available (cosine -> 0).
     */
    fun generateEmbedding(text: String): FloatArray {
        if (!sessionAvailable || ortEnv == null || ortSession == null) {
            return FloatArray(EMBED_DIM) { 0f }
        }

        try {
            val tokenIds = tokenize(text)
            val attentionMask = LongArray(tokenIds.size) { if (tokenIds[it] == 0L) 0L else 1L }
            val tokenTypeIds = LongArray(tokenIds.size) { 0L }

            val inputTensor = OnnxTensor.createTensor(ortEnv, arrayOf(tokenIds))
            val attentionTensor = OnnxTensor.createTensor(ortEnv, arrayOf(attentionMask))
            val typeTensor = OnnxTensor.createTensor(ortEnv, arrayOf(tokenTypeIds))

            val inputs = mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to attentionTensor,
                "token_type_ids" to typeTensor
            )

            ortSession?.run(inputs)?.use { results ->
                val output = results[0].value as Array<Array<FloatArray>>
                val sequenceEmbeddings = output[0]

                val pooled = FloatArray(EMBED_DIM)
                var validTokens = 0
                for (i in sequenceEmbeddings.indices) {
                    if (i < attentionMask.size && attentionMask[i] == 1L) {
                        for (j in 0 until EMBED_DIM) {
                            pooled[j] += sequenceEmbeddings[i][j]
                        }
                        validTokens++
                    }
                }

                var norm = 0.0f
                if (validTokens > 0) {
                    for (j in 0 until EMBED_DIM) {
                        pooled[j] /= validTokens
                        norm += pooled[j] * pooled[j]
                    }
                }

                val sqrtNorm = sqrt(norm)
                if (sqrtNorm > 0.0f) {
                    for (j in 0 until EMBED_DIM) {
                        pooled[j] /= sqrtNorm
                    }
                }

                return pooled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embedding generation failed for text: $text", e)
        }

        return FloatArray(EMBED_DIM) { 0f }
    }

    /**
     * Finds the best matching scam category for a given segment transcript.
     * Returns (similarity, categoryId); similarity <= threshold yields null category.
     */
    fun findBestMatch(transcript: String, threshold: Float = 0.65f): Pair<Float, String?> {
        if (transcript.isBlank()) return Pair(-1f, null)

        return if (sessionAvailable) {
            semanticBestMatch(transcript, threshold)
        } else {
            lexicalBestMatch(transcript, threshold)
        }
    }

    private fun semanticBestMatch(transcript: String, threshold: Float): Pair<Float, String?> {
        val segmentEmbedding = generateEmbedding(transcript)
        var bestSimilarity = -1.0f
        var bestCategory: String? = null

        for ((phrase, category) in ScamPhraseLibrary.getFlattenedPhrases()) {
            val cachedEmbedding = phraseEmbeddings[phrase] ?: continue
            val similarity = cosineSimilarity(segmentEmbedding, cachedEmbedding)

            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestCategory = category.id
            }
        }

        if (bestSimilarity >= threshold) {
            return Pair(bestSimilarity, bestCategory)
        }
        return Pair(bestSimilarity, null)
    }

    private fun lexicalBestMatch(transcript: String, threshold: Float): Pair<Float, String?> {
        var bestSimilarity = -1.0f
        var bestCategory: String? = null

        for ((phrase, category) in ScamPhraseLibrary.getFlattenedPhrases()) {
            val similarity = LexicalScamMatcher.score(transcript, phrase)
            if (similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestCategory = category.id
            }
        }

        if (bestSimilarity >= threshold) {
            return Pair(bestSimilarity, bestCategory)
        }
        return Pair(bestSimilarity.coerceAtLeast(0f), null)
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dotProduct = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        if (norm1 == 0.0f || norm2 == 0.0f) return 0.0f
        return dotProduct / (sqrt(norm1) * sqrt(norm2))
    }

    private fun tokenize(text: String): LongArray {
        return tokenizer?.tokenize(text) ?: LongArray(128) { 0L }
    }
}
