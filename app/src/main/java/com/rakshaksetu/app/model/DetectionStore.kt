package com.rakshaksetu.app.model

import android.content.Context
import android.content.SharedPreferences
import com.rakshaksetu.app.security.CallIdValidator

/**
 * Stores the most recent DetectionResult for instant UI display & evidence retrieval.
 */
object DetectionStore {
    private const val PREFS_NAME = "rakshak_detection_store"
    private const val KEY_LAST_RESULT_JSON = "key_last_result_json"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveLastResult(context: Context, result: DetectionResult) {
        try {
            getPrefs(context).edit()
                .putString(KEY_LAST_RESULT_JSON, result.toJson())
                .apply()
        } catch (e: Exception) {
            // Ignore error
        }
    }

    fun getLastResult(context: Context): DetectionResult? {
        return try {
            val json = getPrefs(context).getString(KEY_LAST_RESULT_JSON, null)
            if (json.isNullOrBlank()) null else DetectionResult.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
