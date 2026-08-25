package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

/**
 * Federated Learning dynamic per-category similarity thresholds.
 * When a Context is attached (production), thresholds persist across process death
 * in SharedPreferences; without one (JVM unit tests) behavior stays in-memory.
 */
object FederatedLearningManager {
    private const val TAG = "FederatedLearningManager"
    private const val DEFAULT_THRESHOLD = 0.65f
    private const val PENALTY_STEP = 0.02f // Increase threshold by this amount on false positive
    private const val MAX_THRESHOLD = 0.85f
    private const val MIN_THRESHOLD = 0.55f
    private const val PREFS_NAME = "rakshak_fl_thresholds"
    private const val KEY_THRESHOLDS_JSON = "key_fl_thresholds_json"

    // In-memory map of category ID to its current similarity threshold
    private val categoryThresholds = ConcurrentHashMap<String, Float>()
    private val localDeltas = mutableListOf<WeightDelta>()
    @Volatile private var prefsContext: Context? = null

    /**
     * Attach an application context and restore any persisted thresholds.
     * Safe to call repeatedly; only the first attachment loads state.
     */
    fun attach(context: Context) {
        if (prefsContext != null) return
        synchronized(this) {
            if (prefsContext != null) return
            val appContext = context.applicationContext
            prefsContext = appContext
            try {
                val json = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_THRESHOLDS_JSON, null)
                if (!json.isNullOrBlank()) {
                    val type = object : TypeToken<Map<String, Float>>() {}.type
                    val restored: Map<String, Float> = Gson().fromJson(json, type)
                    categoryThresholds.putAll(restored)
                    Log.i(TAG, "Restored ${restored.size} FL category thresholds.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore FL thresholds", e)
            }
        }
    }

    private fun persist() {
        val ctx = prefsContext ?: return
        try {
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THRESHOLDS_JSON, Gson().toJson(categoryThresholds.toMap()))
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist FL thresholds", e)
        }
    }

    /**
     * Get the dynamic threshold for a specific category.
     */
    fun getThresholdForCategory(categoryId: String): Float {
        return (categoryThresholds[categoryId] ?: DEFAULT_THRESHOLD).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
    }

    /**
     * Log a false positive event. Raises the threshold for that category so it is
     * less likely to trigger falsely again.
     */
    fun logFalsePositive(categoryId: String) {
        val current = getThresholdForCategory(categoryId)
        val updated = (current + PENALTY_STEP).coerceAtMost(MAX_THRESHOLD)

        categoryThresholds[categoryId] = updated

        localDeltas.add(
            WeightDelta(
                categoryId = categoryId,
                oldThreshold = current,
                newThreshold = updated
            )
        )
        persist()

        Log.i(TAG, "FL: Raised threshold for $categoryId from $current to $updated due to false positive.")
    }

    /**
     * Log a confirmed true positive. Gently lowers the threshold (bounded) so the
     * policy can re-adapt when scammers change scripts.
     */
    fun logTruePositive(categoryId: String) {
        val current = getThresholdForCategory(categoryId)
        val updated = (current - PENALTY_STEP / 2).coerceAtLeast(MIN_THRESHOLD)
        if (updated != current) {
            categoryThresholds[categoryId] = updated
            persist()
            Log.i(TAG, "FL: Lowered threshold for $categoryId from $current to $updated after true positive.")
        }
    }

    /**
     * Export the accumulated local weight deltas as a JSON payload for the Backend
     * aggregation server. Clears locally staged deltas after export.
     */
    @Synchronized
    fun exportDeltas(deviceIdHash: String): String {
        val payload = FlExportPayload(
            deviceIdHash = deviceIdHash,
            deltas = localDeltas.toList()
        )
        val json = Gson().toJson(payload)
        localDeltas.clear()
        return json
    }

    /**
     * Apply a global federated learning update (aggregated average threshold per category).
     */
    fun applyGlobalUpdate(globalThresholds: Map<String, Float>) {
        globalThresholds.forEach { (catId, newThreshold) ->
            val bounded = newThreshold.coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)
            categoryThresholds[catId] = bounded
            Log.i(TAG, "FL: Applied global threshold for $catId -> $bounded")
        }
        persist()
    }
}
