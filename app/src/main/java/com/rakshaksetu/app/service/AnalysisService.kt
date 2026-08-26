package com.rakshaksetu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rakshaksetu.app.BuildConfig
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.elder.ElderModeStore
import com.rakshaksetu.app.elder.EmergencyDispatcher
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.pipeline.ModelDownloadManager
import com.rakshaksetu.app.pipeline.PipelineCoordinator
import com.rakshaksetu.app.pipeline.VoskAsrEngine
import com.rakshaksetu.app.pipeline.VotingEngine
import com.rakshaksetu.app.security.CallIdValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * ForegroundService hosting the post-call AI analysis pipeline.
 *
 * Survival guarantees on aggressive OEM ROMs (HiOS/MIUI/ColorOS):
 *  S1: foregroundServiceType="specialUse" declared with subtype property (Android 14+).
 *  S2: START_STICKY + PendingCallStore — a killed service resumes analysis from the
 *      persisted record when the OS restarts it with a null intent.
 *  S3: WakeLock held for the full inference with a bounded watchdog; release is
 *      guaranteed in both the coroutine finally-block and onDestroy.
 *  S4: Single-flight guard — duplicate triggers (receiver + callback race) collapse
 *      into one running job per callId.
 */
class AnalysisService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var alertManager: ScamAlertManager
    private val activeCallId = AtomicReference<String?>(null)

    companion object {
        private const val TAG = "AnalysisService"
        const val CHANNEL_ID = "shield_status"
        const val CHANNEL_DIAGNOSTIC_ID = "analysis_diagnostics"
        const val NOTIFICATION_ID = 1
        const val DIAGNOSTIC_NOTIFICATION_ID_BASE = 2000
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_PHONE_NUMBER = "EXTRA_PHONE_NUMBER"
        const val EXTRA_DURATION_SEC = "EXTRA_DURATION_SEC"
        const val EXTRA_IS_SIMULATION = "EXTRA_IS_SIMULATION"

        private const val WAKELOCK_WATCHDOG_MS = 10 * 60 * 1000L
        private const val MAX_RESUME_ATTEMPTS = 5

        /**
         * OEM kill-recovery entry point. HiOS/MIUI swipe-up kills terminate even
         * foreground services and block sticky restarts; the persisted PendingCallStore
         * record lets us resume the interrupted analysis on next app open or boot.
         * Called with a bare intent so onStartCommand takes the resume path.
         */
        fun resumeIfPending(context: Context) {
            val appContext = context.applicationContext
            val pending = PendingCallStore.get(appContext) ?: return
            try {
                val intent = Intent(appContext, AnalysisService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
                Log.i(TAG, "Resuming interrupted analysis for callId=${pending.callId}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume pending analysis (background start blocked?)", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        ensureNotificationChannels()
        alertManager = ScamAlertManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // S2: Sticky restart always delivers a null intent — resume persisted work.
        var callId = intent?.getStringExtra(EXTRA_CALL_ID)
        var phoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        var durationSec = intent?.getIntExtra(EXTRA_DURATION_SEC, 0)
        var isSimulation = intent?.getBooleanExtra(EXTRA_IS_SIMULATION, false) ?: false

        if (callId == null) {
            val pending = PendingCallStore.get(this)
            if (pending == null) {
                Log.w(TAG, "Restart without intent and no pending record. Stopping.")
                return stopAndReturn()
            }
            val attempts = PendingCallStore.incrementAttempts(this)
            if (attempts > MAX_RESUME_ATTEMPTS) {
                Log.w(TAG, "Pending call exceeded $MAX_RESUME_ATTEMPTS resume attempts. Enqueuing WorkManager fallback.")
                enqueueWorkManagerFallback(pending)
                PendingCallStore.clear(this)
                return stopAndReturn()
            }
            Log.i(TAG, "Sticky resume attempt $attempts for callId=${pending.callId}")
            callId = pending.callId
            phoneNumber = pending.phoneNumber
            durationSec = pending.durationSec
            isSimulation = false
        }

        if (!CallIdValidator.isValid(callId)) {
            Log.e(TAG, "Invalid callId rejected: $callId")
            return stopAndReturn()
        }

        // S4: single-flight per callId
        if (!activeCallId.compareAndSet(null, callId)) {
            if (activeCallId.get() == callId) {
                Log.d(TAG, "Duplicate trigger suppressed for callId=$callId")
            } else {
                Log.d(TAG, "Another analysis in flight; re-persisting pending call.")
                PendingCallStore.save(
                    this,
                    PendingCallStore.PendingCallRecord(callId!!, phoneNumber ?: "Unknown", durationSec ?: 0, System.currentTimeMillis())
                )
                return START_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildProgressNotification(phoneNumber ?: ""))
        acquireWakeLock()

        val fCallId = callId
        val fPhone = phoneNumber ?: "Unknown"
        val fDuration = durationSec ?: 0
        val fSim = isSimulation && BuildConfig.DEBUG // simulations never run in release builds

        serviceScope.launch {
            try {
                // DPDP Consent Check
                val consentStore = com.rakshaksetu.app.consent.ConsentStore(applicationContext)
                if (!consentStore.isShieldActive) {
                    Log.d(TAG, "Shield is PAUSED by user. Skipping analysis for callId=$fCallId")
                    PendingCallStore.clear(applicationContext)
                    return@launch
                }

                Log.d(TAG, "Pipeline begin for callId=$fCallId (sim=$fSim)")
                val startTimeMs = System.currentTimeMillis()

                val result = if (fSim) {
                    DetectionStore.getLastResult(applicationContext)
                        ?: FakePipelineEmitter.scamResult()
                } else {
                    val asrEngine = VoskAsrEngine(applicationContext)
                    val coordinator = PipelineCoordinator(
                        applicationContext,
                        asrEngine,
                        VotingEngine()
                    )

                    val destWavPath =
                        File(applicationContext.cacheDir, "call_$fCallId.wav").absolutePath

                    coordinator.runPipeline(
                        callId = fCallId,
                        phoneNumber = fPhone,
                        callDurationSec = fDuration,
                        callEndEpoch = System.currentTimeMillis(),
                        destWavPath = destWavPath
                    )
                    // null == recording genuinely unavailable -> handled below, NEVER faked
                }

                val durationMs = System.currentTimeMillis() - startTimeMs
                BatteryMonitor.logAnalysisComplete(applicationContext, durationMs)

                if (result == null) {
                    Log.w(TAG, "Recording unavailable for callId=$fCallId — emitting diagnostic only.")
                    notifyDiagnostic(
                        fCallId,
                        "Could not locate the call recording after hangup.",
                        "The OEM recorder may not have saved it, or indexing took too long. No verdict was produced."
                    )
                    return@launch
                }

                Log.d(TAG, "Pipeline complete in ${durationMs}ms: isScam=${result.isScam}, confidence=${result.confidence}")

                DetectionStore.saveLastResult(applicationContext, result)
                try {
                    com.rakshaksetu.app.evidence.StatementGenerator.saveEvidence(applicationContext, result)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not generate evidence statement file", e)
                }

                alertManager.showScamAlert(result)

                // M3: Elder Mode emergency dispatch (opt-in auto-send path)
                val elderStore = ElderModeStore(applicationContext)
                if (elderStore.isEnabled &&
                    elderStore.autoSendSmsEnabled &&
                    EmergencyDispatcher.qualifiesForAutoSend(result)
                ) {
                    EmergencyDispatcher.dispatch(applicationContext, result, autoTriggered = true)
                }

                Log.d(TAG, "Result emitted for callId=$fCallId")
            } catch (e: Exception) {
                // T-CRITICAL: Pipeline crash must NOT bring down the service silently
                Log.e(TAG, "Pipeline failed for callId=$fCallId", e)
            } finally {
                File(cacheDir, "call_$fCallId.wav").delete()
                PendingCallStore.clear(applicationContext)
                activeCallId.compareAndSet(fCallId, null)
                releaseWakeLock()
                stopSelf(startId)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        releaseWakeLock()
        serviceScope.cancel()
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
            setReferenceCounted(false)
            acquire(WAKELOCK_WATCHDOG_MS)
        }
        Log.d(TAG, "WakeLock acquired (watchdog ${WAKELOCK_WATCHDOG_MS / 1000}s)")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            try {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            } catch (e: Exception) {
                Log.w(TAG, "WakeLock release issue", e)
            }
        }
        wakeLock = null
    }

    private fun notifyDiagnostic(relatedCallId: String, title: String, detail: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_DIAGNOSTIC_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()
            nm.notify((DIAGNOSTIC_NOTIFICATION_ID_BASE + relatedCallId.hashCode()).let { if (it < 0) -it else it }, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Diagnostic notification failed", e)
        }
    }

    private fun enqueueWorkManagerFallback(pending: PendingCallStore.PendingCallRecord) {
        try {
            val workData = androidx.work.workDataOf(
                CallAnalysisWorker.KEY_CALL_ID to pending.callId,
                CallAnalysisWorker.KEY_PHONE_NUMBER to pending.phoneNumber,
                CallAnalysisWorker.KEY_DURATION_SEC to pending.durationSec
            )
            val request = androidx.work.OneTimeWorkRequestBuilder<CallAnalysisWorker>()
                .setInputData(workData)
                .build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueue(request)
            Log.i(TAG, "Enqueued WorkManager fallback for callId=${pending.callId}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue WorkManager fallback", e)
        }
    }

    internal fun buildProgressNotification(phoneNumber: String): Notification {
        val maskedPhone = if (phoneNumber.isNotBlank()) ScamAlertManager.maskNumberForDisplay(phoneNumber) else ""
        val content = if (maskedPhone.isNotBlank())
            "Checking call from $maskedPhone for safety..."
        else
            "Checking recent call for safety..."

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🛡️ Rakshak Setu")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(android.graphics.Color.parseColor("#1976D2")) // Trustworthy blue
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun ensureNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Shield Status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows progress of call analysis"
                }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_DIAGNOSTIC_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DIAGNOSTIC_ID, "Analysis Diagnostics", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Non-alert status messages about skipped analyses"
                }
            )
        }
    }
}
