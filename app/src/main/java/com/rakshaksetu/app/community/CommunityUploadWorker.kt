package com.rakshaksetu.app.community

import android.content.Context
import android.util.Log
import androidx.work.*
import com.rakshaksetu.app.community.db.BlacklistSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Deferred, battery-safe worker for community blacklist synchronization and
 * local database hygiene. Runs only while charging on an unmetered network.
 *
 * Remote behavior is entirely mediated by [BlacklistRemoteSync]; with Firebase
 * unwired the worker stays 100% offline (maintenance + retention only).
 */
class CommunityUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CommunityUploadWorker"
        private const val UNIQUE_WORK_NAME = "rakshak_community_sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<CommunityUploadWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
            Log.d(TAG, "Scheduled battery-optimized community sync (Charging + Wi-Fi)")
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Executing deferred community sync while device is charging on Wi-Fi...")
        return try {
            withContext(Dispatchers.IO) {
                val repo = BlacklistRepository(applicationContext)
                repo.ensureSeeded()

                val purged = repo.maintenancePurge()
                if (purged > 0) Log.d(TAG, "Purged $purged stale blacklist entries.")

                // Push path — no-op upload when offline (entries retained locally)
                val remote = BlacklistRemoteSyncFactory.create()
                val outbound = repo.exportForCommunityUpload()
                    .filter { it.source == BlacklistSources.LOCAL_REPORT }
                if (outbound.isNotEmpty()) {
                    remote.push(outbound)
                }

                // Pull path
                if (remote.isRemoteConfigured()) {
                    val watermark = 0L // full refresh; server-side delta filtering applies
                    val incoming = remote.pull(watermark)
                    if (incoming.isNotEmpty()) {
                        val merged = repo.mergeRemote(incoming)
                        Log.i(TAG, "Merged $merged community entries from remote.")
                    }
                } else {
                    Log.d(TAG, "Firebase unavailable — running in Local-Only mode.")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed, will retry later", e)
            Result.retry()
        }
    }
}
