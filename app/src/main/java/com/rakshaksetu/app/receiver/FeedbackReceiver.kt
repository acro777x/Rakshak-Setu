package com.rakshaksetu.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rakshaksetu.app.feedback.FeedbackLogger
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.security.CallIdValidator

/**
 * Handles "Not a Scam" action from notification.
 * Logs false-positive feedback and dismisses notification.
 */
class FeedbackReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "FeedbackReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ScamAlertManager.ACTION_NOT_SCAM) {
            val callId = intent.getStringExtra(ScamAlertManager.EXTRA_CALL_ID) ?: return
            
            if (!CallIdValidator.isValid(callId)) {
                Log.e(TAG, "Invalid callId in feedback: $callId")
                return
            }
            
            Log.d(TAG, "User reported not-a-scam for callId=$callId")
            
            try {
                val logger = FeedbackLogger(context)
                logger.logNotScam(callId, reason = "User dismissed from notification")

                // Close the federated-learning loop: a user telling us "not a scam"
                // is the single highest-quality negative signal the app can get.
                // Previously logFalsePositive() had no production caller at all, so
                // the model could never learn from its own false alarms.
                //
                // Only the matched CATEGORY is fed back -- never audio, transcript,
                // or the caller's number -- and the category string is validated
                // against the loaded corpus so a malformed value cannot enter the
                // threshold table.
                val category = DetectionStore.getCachedResult(callId)?.scamType
                    ?: DetectionStore.getLastResult(context)?.takeIf { it.callId == callId }?.scamType
                if (!category.isNullOrBlank() && category.length <= 64) {
                    com.rakshaksetu.app.pipeline.FederatedLearningManager.attach(context)
                    com.rakshaksetu.app.pipeline.FederatedLearningManager.logFalsePositive(category)
                    Log.i(TAG, "FL: recorded false positive for category=$category")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log feedback", e)
            }
        }
    }
}
