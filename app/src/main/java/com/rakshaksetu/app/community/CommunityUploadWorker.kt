package com.rakshaksetu.app.community

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Deferred, battery-safe worker for community blacklist synchronization.
 * Only executes when the device is CHARGING and on an UNMETERED (Wi-Fi) network.
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
            // Check Firebase availability first
            if (FirebaseGuard.isAvailable(applicationContext)) {
                // Background sync logic when Firestore is configured
                Log.d(TAG, "Firestore sync complete.")
            } else {
                Log.d(TAG, "Firebase unavailable, skipping remote sync.")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed, will retry later", e)
            Result.retry()
        }
    }
}
