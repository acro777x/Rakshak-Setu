package com.rakshaksetu.app.community

import android.util.Log
import com.rakshaksetu.app.community.db.BlacklistEntry

/**
 * Pluggable community blacklist synchronization seam.
 * Production currently runs [NoopRemoteSync] (fully offline). When Firebase is wired
 * (google-services.json added + firestore-ktx dependency enabled), implement this
 * interface with a Firestore-backed adapter registered in [Factory] — no other
 * call-site changes required.
 */
interface BlacklistRemoteSync {
    /** Pull entries reported to the community after [sinceEpochMs]. */
    suspend fun pull(sinceEpochMs: Long): List<BlacklistEntry>

    /** Push locally-reported entries with >= min reports. Returns success flag. */
    suspend fun push(entries: List<BlacklistEntry>): Boolean

    fun isRemoteConfigured(): Boolean = false

    object NoopRemoteSync : BlacklistRemoteSync {
        override suspend fun pull(sinceEpochMs: Long): List<BlacklistEntry> = emptyList()
        override suspend fun push(entries: List<BlacklistEntry>): Boolean {
            Log.d("BlacklistRemoteSync", "Offline mode: ${entries.size} local reports retained for later upload.")
            return true
        }
    }
}

object BlacklistRemoteSyncFactory {
    @Volatile private var override: BlacklistRemoteSync? = null

    /** Test/Firebase bootstrap hook. Pass null to reset to offline default. */
    fun setImplementation(impl: BlacklistRemoteSync?) {
        override = impl
    }

    fun create(): BlacklistRemoteSync = override ?: BlacklistRemoteSync.NoopRemoteSync
}
