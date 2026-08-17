package com.rakshaksetu.app.consent

import android.content.Context
import android.content.SharedPreferences

/**
 * DPDP Act compliant local consent store.
 * Tracks user consent state: ACTIVE (monitoring enabled) / PAUSED (monitoring stopped).
 */
class ConsentStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rakshak_consent_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SHIELD_ENABLED = "key_shield_enabled"
        private const val KEY_CONSENT_TIMESTAMP = "key_consent_timestamp"
    }

    var isShieldActive: Boolean
        get() = prefs.getBoolean(KEY_SHIELD_ENABLED, true)
        set(value) {
            prefs.edit()
                .putBoolean(KEY_SHIELD_ENABLED, value)
                .putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
                .apply()
        }

    val lastConsentEpoch: Long
        get() = prefs.getLong(KEY_CONSENT_TIMESTAMP, 0L)

    fun purgeEvidence(context: Context) {
        try {
            val evidenceDir = java.io.File(context.filesDir, "evidence")
            if (evidenceDir.exists()) {
                evidenceDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // Ignore error
        }
    }
}

