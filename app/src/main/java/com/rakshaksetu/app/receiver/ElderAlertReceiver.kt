package com.rakshaksetu.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rakshaksetu.app.elder.EmergencyDispatcher
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.security.CallIdValidator
import android.telephony.SmsManager

/**
 * Receives two event families:
 *  1. ACTION_ALERT_FAMILY — user tapped "Alert Family" (one-tap dispatch).
 *  2. ACTION_SMS_SENT — delivery-intent echo from [SmsManager]; logged only.
 */
class ElderAlertReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ALERT_FAMILY = "com.rakshaksetu.app.ACTION_ALERT_FAMILY"
        const val ACTION_SMS_SENT = "com.rakshaksetu.app.ACTION_ELDER_SMS_SENT"
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_GUARDIAN_NUMBER = "EXTRA_GUARDIAN_NUMBER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_ALERT_FAMILY -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: return
                if (!CallIdValidator.isValid(callId)) {
                    Log.e("ElderAlertReceiver", "Invalid callId in alert-family request")
                    return
                }
                val result = DetectionStore.getCachedResult(callId)
                    ?: DetectionStore.getLastResult(appContext)?.takeIf { it.callId == callId }
                if (result == null || !EmergencyDispatcher.qualifiesForOneTap(result)) {
                    Log.w("ElderAlertReceiver", "No qualifying result for callId=$callId")
                    return
                }
                try {
                    val sent = EmergencyDispatcher.dispatchBlocking(appContext, result, autoTriggered = false)
                    Log.i("ElderAlertReceiver", "One-tap family alert delivered to $sent guardian(s).")
                } catch (e: Exception) {
                    Log.e("ElderAlertReceiver", "Family alert dispatch failed", e)
                }
            }
            ACTION_SMS_SENT -> {
                val guardian = intent.getStringExtra(EXTRA_GUARDIAN_NUMBER) ?: "unknown"
                Log.i("ElderAlertReceiver", "Emergency SMS result code=${resultCode} for guardian=$guardian")
            }
        }
    }
}
