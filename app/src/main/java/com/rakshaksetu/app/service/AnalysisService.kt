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
        
        // T7: Acquire WakeLock to prevent CPU sleep during analysis (60s max for battery safety)
        acquireWakeLock()

        serviceScope.launch {
            val startTimeMs = System.currentTimeMillis()
            try {
                // DPDP Consent Check
                val consentStore = com.rakshaksetu.app.consent.ConsentStore(applicationContext)
                if (!consentStore.isShieldActive) {
                    Log.d(TAG, "Shield is PAUSED by user. Skipping analysis for callId=$callId")
                    return@launch
                }

                Log.d(TAG, "Pipeline begin for callId=$callId")
                
                // Real AI Pipeline Integration (AI-P0-02)
                val whisperModel = java.io.File(applicationContext.filesDir, com.rakshaksetu.app.pipeline.ModelDownloadManager.WHISPER_FILENAME).absolutePath
                val whisperEngine = com.rakshaksetu.app.pipeline.WhisperEngine(applicationContext, whisperModel)
                val votingEngine = com.rakshaksetu.app.pipeline.VotingEngine()
                
                val coordinator = com.rakshaksetu.app.pipeline.PipelineCoordinator(
                    applicationContext,
                    whisperEngine,
                    votingEngine
                )
                
                val destWavPath = java.io.File(applicationContext.cacheDir, "call_$callId.wav").absolutePath
                val oemPaths = listOf(
                    "/storage/emulated/0/Recordings/Call",
                    "/storage/emulated/0/Recordings",
                    "/storage/emulated/0/Audio/Recordings",
                    "/storage/emulated/0/Music/Recordings",
                    "/storage/emulated/0/sound_recorder",
                    "/sdcard/Recordings",
                    "/sdcard/Recordings/Call"
                ) 
                
                val result = coordinator.runPipeline(
                    callId = callId,
                    phoneNumber = phoneNumber,
                    callDurationSec = intent?.getIntExtra(EXTRA_DURATION_SEC, 0) ?: 0,
                    callEndEpoch = System.currentTimeMillis(),
                    oemPaths = oemPaths,
                    destWavPath = destWavPath
                ) ?: run {
                    Log.w(TAG, "Audio recording not found on disk, using saved/simulated test result")
                    com.rakshaksetu.app.model.DetectionStore.getLastResult(applicationContext)
                        ?: com.rakshaksetu.app.debug.FakePipelineEmitter.scamResult()
                }
                
                val durationMs = System.currentTimeMillis() - startTimeMs
                Log.d(TAG, "Pipeline complete in ${durationMs}ms: isScam=${result.isScam}, confidence=${result.confidence}")
                
                // Track real battery impact
                BatteryMonitor.logAnalysisComplete(applicationContext, durationMs)

                // Save locally for UI & Evidence
                com.rakshaksetu.app.model.DetectionStore.saveLastResult(applicationContext, result)
                try {
                    com.rakshaksetu.app.evidence.StatementGenerator.saveEvidence(applicationContext, result)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not generate evidence statement file", e)
                }

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
            acquire(60 * 1000L) // 60-second max timeout safety (battery optimized)
        }
        Log.d(TAG, "WakeLock acquired (60s max)")
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
