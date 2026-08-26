package com.rakshaksetu.app.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.pipeline.PipelineCoordinator
import com.rakshaksetu.app.pipeline.VoskAsrEngine
import com.rakshaksetu.app.pipeline.VotingEngine
import java.io.File

/**
 * WorkManager fallback worker for post-call AI analysis.
 * Enqueued when aggressive OEM battery management kills the foreground service repeatedly.
 * Guaranteed to execute via Android JobScheduler even after process death.
 */
class CallAnalysisWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "CallAnalysisWorker"
        const val KEY_CALL_ID = "KEY_CALL_ID"
        const val KEY_PHONE_NUMBER = "KEY_PHONE_NUMBER"
        const val KEY_DURATION_SEC = "KEY_DURATION_SEC"
    }

    override suspend fun doWork(): Result {
        val callId = inputData.getString(KEY_CALL_ID) ?: return Result.failure()
        val phoneNumber = inputData.getString(KEY_PHONE_NUMBER) ?: "Unknown"
        val durationSec = inputData.getInt(KEY_DURATION_SEC, 0)

        Log.i(TAG, "Executing WorkManager fallback analysis for callId=$callId")

        return try {
            val asrEngine = VoskAsrEngine(appContext)
            val coordinator = PipelineCoordinator(appContext, asrEngine, VotingEngine())
            val destWavPath = File(appContext.cacheDir, "work_$callId.wav").absolutePath

            val result = coordinator.runPipeline(
                callId = callId,
                phoneNumber = phoneNumber,
                callDurationSec = durationSec,
                callEndEpoch = System.currentTimeMillis(),
                destWavPath = destWavPath
            )

            if (result != null) {
                DetectionStore.saveLastResult(appContext, result)
                if (result.isScam) {
                    val alertManager = ScamAlertManager(appContext)
                    alertManager.showScamAlert(result)
                }
            }

            File(destWavPath).delete()
            PendingCallStore.clear(appContext)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager analysis failed for callId=$callId", e)
            Result.retry()
        }
    }
}
