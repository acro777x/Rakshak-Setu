package com.rakshaksetu.app.pipeline

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Log
import kotlin.math.sqrt

object EmbeddingEngine {
    private const val TAG = "EmbeddingEngine"
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: WordPieceTokenizer? = null

    // Cache of embedded scam phrases
    private val phraseEmbeddings = mutableMapOf<String, FloatArray>()

    fun init(context: android.content.Context, modelPath: String) {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            ortSession = ortEnv?.createSession(modelPath, OrtSession.SessionOptions())
            tokenizer = WordPieceTokenizer(context, "vocab.txt")
            Log.i(TAG, "ONNX Runtime initialized with model: $modelPath")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize ONNX Runtime", e)
        }
    }

    /**
     * Embed the scam phrase library and cache the vectors.
     */
    fun precomputeLibraryEmbeddings(phrases: List<Pair<String, ScamCategory>>) {
        phrases.forEach { (phrase, _) ->
            if (!phraseEmbeddings.containsKey(phrase)) {
                phraseEmbeddings[phrase] = generateEmbedding(phrase)
            }
        }
    }

    /**
     * Generates a 384-dimensional embedding for the given text using ONNX Runtime.
     * Note: This requires a tokenizer step (e.g., WordPiece/SentencePiece) before passing to ONNX.
     */
    fun generateEmbedding(text: String): FloatArray {
        if (ortSession == null || ortEnv == null) {
            Log.w(TAG, "ONNX Session not initialized. Returning dummy embedding.")
            return FloatArray(384) { 0f }
        }

        try {
            // 1. Tokenize text (Implementation depends on the specific tokenizer used for MiniLM)
            val tokenIds = tokenize(text)
            val attentionMask = LongArray(tokenIds.size) { if (tokenIds[it] == 0L) 0L else 1L }
            // token_type_ids is often required for BERT/MiniLM
            val tokenTypeIds = LongArray(tokenIds.size) { 0L }

            // 2. Prepare ONNX Inputs
            val inputTensor = OnnxTensor.createTensor(ortEnv, arrayOf(tokenIds))
            val attentionTensor = OnnxTensor.createTensor(ortEnv, arrayOf(attentionMask))
            val typeTensor = OnnxTensor.createTensor(ortEnv, arrayOf(tokenTypeIds))
            
            val inputs = mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to attentionTensor,
                "token_type_ids" to typeTensor
            )

            // 3. Run Inference
            ortSession?.run(inputs)?.use { results ->
                // 4. Extract embeddings and apply Mean Pooling
                val output = results[0].value as Array<Array<FloatArray>>
                val sequenceEmbeddings = output[0] // shape: [seq_len, 384]
                
                val pooled = FloatArray(384)
                var validTokens = 0
                for (i in sequenceEmbeddings.indices) {
                    if (attentionMask[i] == 1L) {
                        for (j in 0 until 384) {
                            pooled[j] += sequenceEmbeddings[i][j]
                        }
                        validTokens++
                    }
                }
                
                // Average
                var norm = 0.0f
                if (validTokens > 0) {
                    for (j in 0 until 384) {
                        pooled[j] /= validTokens
                        norm += pooled[j] * pooled[j]
                    }
                }
                
                // L2 Normalization (crucial for cosine similarity with sentence-transformers)
                val sqrtNorm = sqrt(norm)
                if (sqrtNorm > 0.0f) {
                    for (j in 0 until 384) {
                        pooled[j] /= sqrtNorm
                    }
                }
                
                return pooled
            }
        } catch (e: Exception) {
            Log.e(TAG, "Embedding generation failed for text: $text", e)
        }
        
        return FloatArray(384) { 0f }
    }

    /**
     * Finds the best matching scam category for a given segment transcript.
     * Returns the similarity score and the matched category ID.
     */
    fun findBestMatch(transcript: String, threshold: Float = 0.80f): Pair<Float, String?> {
        val segmentEmbedding = generateEmbedding(transcript)
        var bestSimilarity = -1.0f
        var bestCategory: String? = null

        val flattened = ScamPhraseLibrary.getFlattenedPhrases()
        
        for ((phrase, category) in flattened) {
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
