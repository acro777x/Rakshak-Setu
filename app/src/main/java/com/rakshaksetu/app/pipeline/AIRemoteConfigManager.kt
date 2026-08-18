package com.rakshaksetu.app.pipeline

import android.util.Log
// import com.google.firebase.remoteconfig.FirebaseRemoteConfig
// import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

object AIRemoteConfigManager {
    private const val TAG = "AIRemoteConfigManager"

    // Default configuration values
    var simThreshold: Float = 0.80f
        private set
    var voteK: Int = 3
        private set
    var voteWindow: Int = 5
        private set
    var libraryVersion: Int = 1
        private set
    var oemPaths: List<String> = listOf(
        "MIUI/sound_recorder/call_rec/",
        "Call/",
        "Music/CallRecordings/",
        "Recordings/Call Recordings/"
    )
        private set

    /**
     * Initializes the Firebase Remote Config instance and fetches the latest tuning parameters.
     */
    fun fetchAndApplyConfig() {
        Log.i(TAG, "Fetching remote configuration for AI Pipeline...")
        
        // Uncomment below once Firebase is fully added to the project via google-services.json
        /*
        val remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)

        // Set defaults
        val defaults = mapOf(
            "sim_threshold" to 0.80,
            "vote_k" to 3,
            "vote_window" to 5,
            "library_version" to 1,
            "oem_paths" to "MIUI/sound_recorder/call_rec/,Call/,Music/CallRecordings/,Recordings/Call Recordings/"
        )
        remoteConfig.setDefaultsAsync(defaults)

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val updated = task.result
                Log.d(TAG, "Config params updated: $updated")

                simThreshold = remoteConfig.getDouble("sim_threshold").toFloat()
                voteK = remoteConfig.getLong("vote_k").toInt()
                voteWindow = remoteConfig.getLong("vote_window").toInt()
                libraryVersion = remoteConfig.getLong("library_version").toInt()
                
                val pathsStr = remoteConfig.getString("oem_paths")
                oemPaths = pathsStr.split(",").map { it.trim() }

                Log.i(TAG, "Applied AI Remote Config: sim=$simThreshold, k=$voteK, w=$voteWindow, libVer=$libraryVersion")
            } else {
                Log.e(TAG, "Fetch failed, using defaults")
            }
        }
        */
    }
}
