package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class WeightDelta(
    val categoryId: String,
    val oldThreshold: Float,
    val newThreshold: Float,
    val reason: String = "false_positive_correction",
    val timestampMs: Long = System.currentTimeMillis()
)

data class FlExportPayload(
    val deviceIdHash: String, // DPDP compliant anonymized identifier
    val deltas: List<WeightDelta>,
    val version: Int = 1
)

object FederatedLearningManager {
    private const val TAG = "FederatedLearningManager"
    private const val DEFAULT_THRESHOLD = 0.80f
    private const val PENALTY_STEP = 0.02f // Increase threshold by this amount on false positive
    private const val MAX_THRESHOLD = 0.95f

    // In-memory map of category ID to its current similarity threshold
    private val categoryThresholds = ConcurrentHashMap<String, Float>()
    private val localDeltas = mutableListOf<WeightDelta>()

    /**
     * Get the dynamic threshold for a specific category.
     */
    fun getThresholdForCategory(categoryId: String): Float {
        return categoryThresholds[categoryId] ?: DEFAULT_THRESHOLD
    }

    /**
     * Log a false positive event. This indicates the user explicitly marked an alert as safe.
     * We penalize (raise) the threshold for that specific category so it's less likely to trigger falsely.
     */
    fun logFalsePositive(categoryId: String) {
        val current = getThresholdForCategory(categoryId)
        val updated = (current + PENALTY_STEP).coerceAtMost(MAX_THRESHOLD)
        
        categoryThresholds[categoryId] = updated
        
        val delta = WeightDelta(
            categoryId = categoryId,
            oldThreshold = current,
            newThreshold = updated
        )
        localDeltas.add(delta)
        
        Log.i(TAG, "FL: Raised threshold for $categoryId from $current to $updated due to false positive.")
    }

    /**
     * Export the accumulated local weight deltas as a JSON payload for the Backend
     * to push to the central FL Aggregation server (Firestore).
     */
    fun exportDeltas(deviceIdHash: String): String {
        val payload = FlExportPayload(
            deviceIdHash = deviceIdHash,
            deltas = localDeltas.toList()
        )
        val json = Gson().toJson(payload)
        
        // Clear locally staged deltas after export
        localDeltas.clear()
        
        return json
    }

    /**
     * Apply a global federated learning update (e.g., from Remote Config or Firestore).
     * The server provides an aggregated average threshold per category.
     */
    fun applyGlobalUpdate(globalThresholds: Map<String, Float>) {
        globalThresholds.forEach { (catId, newThreshold) ->
            // A simple moving average merge could be done here, or just override
            categoryThresholds[catId] = newThreshold
            Log.i(TAG, "FL: Applied global threshold for $catId -> $newThreshold")
        }
    }
}
