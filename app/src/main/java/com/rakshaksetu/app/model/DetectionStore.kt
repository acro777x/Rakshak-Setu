package com.rakshaksetu.app.model

import android.content.Context
import android.content.SharedPreferences

/**
 * RAM-efficient store for caching DetectionResults using a bounded LRU Cache (max 50 entries)
 * and persistent storage for the latest result.
 */
object DetectionStore {
    private const val PREFS_NAME = "rakshak_detection_store"
    private const val KEY_LAST_RESULT_JSON = "key_last_result_json"

    // RAM OPTIMIZATION: Bounded LRU Cache in memory to prevent unbounded heap growth
    private val lruCache = object : LinkedHashMap<String, DetectionResult>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DetectionResult>?): Boolean {
            return size > 50
        }
    }

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun saveLastResult(context: Context, result: DetectionResult) {
        try {
            lruCache[result.callId] = result
            getPrefs(context).edit()
                .putString(KEY_LAST_RESULT_JSON, result.toJson())
                .apply()
        } catch (e: Exception) {
            // Ignore error
        }
    }

    @Synchronized
    fun getLastResult(context: Context): DetectionResult? {
        return try {
            val json = getPrefs(context).getString(KEY_LAST_RESULT_JSON, null)
            if (json.isNullOrBlank()) null else DetectionResult.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun getCachedResult(callId: String): DetectionResult? {
        return lruCache[callId]
    }
}
