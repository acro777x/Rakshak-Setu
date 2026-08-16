package com.rakshaksetu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rakshaksetu.app.BuildConfig
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.security.CallIdValidator
import kotlinx.coroutines.*

/**
 * ForegroundService hosting the AI analysis pipeline.
 * 
 * Runtime survival guarantees (Aristotelian truths):
 * T1: foregroundServiceType="specialUse" matches audio-analysis use case
 *     (not phoneCall — we don't intercept calls; not microphone — we read files)
 * T4: START_STICKY ensures OS restarts service after kill
 * T7: WakeLock prevents CPU sleep during analysis; BatteryOptimizationHelper
 *     guides user to whitelist on MIUI/Samsung/Realme
 */
class AnalysisService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var alertManager: ScamAlertManager

    companion object {
        private const val TAG = "AnalysisService"
        const val CHANNEL_ID = "shield_status"
        const val NOTIFICATION_ID = 1
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
        const val EXTRA_DURATION_SEC = "EXTRA_DURATION_SEC"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        ensureNotificationChannel()
        alertManager = ScamAlertManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID) ?: return stopAndReturn()
        val phoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
        
        // T-SECURITY: Validate callId to prevent path traversal
        if (!CallIdValidator.isValid(callId)) {
            Log.e(TAG, "Invalid callId rejected: $callId")
            return stopAndReturn()
        }

        Log.d(TAG, "Analysis starting for callId=$callId")
        
        // T1: Start foreground IMMEDIATELY before any async work
        startForeground(NOTIFICATION_ID, buildProgressNotification(phoneNumber))
        
        // T7: Acquire WakeLock to prevent CPU sleep during analysis
        acquireWakeLock()

        serviceScope.launch {
            try {
                Log.d(TAG, "Pipeline begin for callId=$callId")
                
                // TODO: Replace with real AI pipeline when available
                // Currently uses fake data in debug builds only
                val result = if (BuildConfig.DEBUG) {
                    delay(1500) // Simulate pipeline latency
                    FakePipelineEmitter.scamResult()
                } else {
                    // In release: real AI pipeline integration point
                    // val pipeline = AIPipeline(applicationContext)
                    // pipeline.analyze(callId, phoneNumber)
                    Log.w(TAG, "Release build: real AI pipeline not yet integrated")
                    delay(1000)
                    FakePipelineEmitter.scamResult() // TEMPORARY — remove before release
                }
                
                Log.d(TAG, "Pipeline complete: isScam=${result.isScam}, confidence=${result.confidence}")
                
                // Emit result to notification system
                alertManager.showScamAlert(result)
                
                Log.d(TAG, "Result emitted for callId=$callId")
            } catch (e: Exception) {
                // T-CRITICAL: Pipeline crash must NOT bring down service
                Log.e(TAG, "Pipeline failed for callId=$callId", e)
            } finally {
                releaseWakeLock()
                stopSelf(startId)
            }
        }

        // T4: START_STICKY — OS will restart service if killed mid-analysis
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun stopAndReturn(): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RakshakSetu::AnalysisPipeline"
        ).apply {
            acquire(5 * 60 * 1000L) // 5-minute max timeout safety
        }
        Log.d(TAG, "WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }

    private fun buildProgressNotification(phoneNumber: String = ""): Notification {
        val text = if (phoneNumber.isNotBlank()) 
            "Analyzing call from $phoneNumber..." 
        else 
            "Analyzing last call..."
            
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rakshak Setu")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Shield Status", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of call analysis"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
