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

    /**
     * Full DPDP erasure (Section 12) — deletes EVERY artefact this app holds.
     *
     * Previously this only removed `filesDir/evidence`, while the UI told the
     * user "All local evidence & audio logs completely purged." That was false:
     * detection results (phone numbers + full transcripts), the community
     * blacklist, speaker voice profiles, UPI threat indicators and the federated
     * -learning threshold table all survived an "erase everything".
     *
     * Callers must pass the Application context.
     *
     * @return number of top-level stores that were cleared, for honest UI copy.
     */
    fun purgeEvidence(context: Context): Int {
        val appContext = context.applicationContext
        var cleared = 0
        val dirs = listOf(
            "evidence",   // evidence dossiers (manifest.json + preserved audio)
            "intel",      // ThreatIntelClient UPI indicators
            "feedback",   // FeedbackLogger not-a-scam records
            "ai_models",  // downloaded ASR / encoder models (regenerable)
            "cache"       // decoded WAV working files
        )
        for (name in dirs) {
            try {
                val d = java.io.File(appContext.filesDir, name)
                if (d.exists() && d.deleteRecursively()) cleared++
            } catch (e: Exception) {
                android.util.Log.w("ConsentStore", "purge failed for $name: ${e.message}")
            }
        }

        // Speaker voice profiles live at filesDir root, not in a subdirectory.
        try {
            val profiles = java.io.File(appContext.filesDir, "speaker_voice_profiles.json")
            if (profiles.exists() && profiles.delete()) cleared++
        } catch (e: Exception) {
            android.util.Log.w("ConsentStore", "purge failed for speaker profiles", e)
        }

        // The blacklist database is a Room DB with its own sidecar files.
        try {
            val db = appContext.getDatabasePath("rakshak_blacklist.db")
            if (appContext.deleteDatabase("rakshak_blacklist.db")) cleared++
            else if (db.exists()) db.delete()
        } catch (e: Exception) {
            android.util.Log.w("ConsentStore", "purge failed for blacklist db", e)
        }

        // SharedPreferences: consent itself, detections, pending calls, call
        // state, FL thresholds, model-download state.
        listOf(
            "rakshak_detection_store", "rakshak_pending_call",
            "rakshak_call_state_tracker", "rakshak_analysis_trigger",
            "rakshak_fl_thresholds", "rakshak_ai_models", "rakshak_fl_sync",
            "rakshak_consent_prefs"
        ).forEach { name ->
            try {
                appContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().apply()
                cleared++
            } catch (e: Exception) {
                android.util.Log.w("ConsentStore", "purge failed for prefs $name", e)
            }
        }

        // External cache, where AudioDecoder writes working WAVs.
        try {
            appContext.externalCacheDir?.deleteRecursively()
        } catch (e: Exception) {
            android.util.Log.w("ConsentStore", "purge failed for external cache", e)
        }

        android.util.Log.i("ConsentStore", "Full local erasure complete ($cleared stores cleared).")
        return cleared
    }
}

