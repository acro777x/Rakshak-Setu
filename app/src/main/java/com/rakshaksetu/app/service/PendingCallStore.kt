package com.rakshaksetu.app.service

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.rakshaksetu.app.security.CallIdValidator

/**
 * Crash-safe record of the call awaiting analysis. Persisted BEFORE the pipeline
 * launches so that a HiOS/MIUI process kill mid-inference can be recovered from
 * on START_STICKY restart (where the redelivered intent is always null).
 */
object PendingCallStore {
    private const val PREFS_NAME = "rakshak_pending_call"
    private const val KEY_RECORD_JSON = "key_record_json"
    private const val KEY_ATTEMPTS = "key_attempts"

    data class PendingCallRecord(
        val callId: String,
        val phoneNumber: String,
        val durationSec: Int,
        val endEpochMs: Long,
        val createdAtMs: Long = System.currentTimeMillis()
    )

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun save(context: Context, record: PendingCallRecord) {
        try {
            getPrefs(context).edit()
                .putString(KEY_RECORD_JSON, Gson().toJson(record))
                .putInt(KEY_ATTEMPTS, 0)
                .commit()
        } catch (ignored: Exception) {
        }
    }

    @Synchronized
    fun get(context: Context): PendingCallRecord? {
        return try {
            val json = getPrefs(context).getString(KEY_RECORD_JSON, null)
            if (json.isNullOrBlank()) return null
            val record = Gson().fromJson(json, PendingCallRecord::class.java)
            if (record != null && CallIdValidator.isValid(record.callId)) record else null
        } catch (e: Exception) {
            null
        }
    }

    @Synchronized
    fun incrementAttempts(context: Context): Int {
        return try {
            val prefs = getPrefs(context)
            val next = prefs.getInt(KEY_ATTEMPTS, 0) + 1
            prefs.edit().putInt(KEY_ATTEMPTS, next).apply()
            next
        } catch (e: Exception) {
            1
        }
    }

    @Synchronized
    fun clear(context: Context) {
        try {
            getPrefs(context).edit().clear().commit()
        } catch (ignored: Exception) {
        }
    }
}
