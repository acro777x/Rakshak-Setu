package com.rakshaksetu.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rakshaksetu.app.feedback.FeedbackLogger
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log feedback", e)
            }
        }
    }
}
