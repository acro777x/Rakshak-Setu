package com.rakshaksetu.app.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import com.rakshaksetu.app.community.PreCallWarningDispatcher
import com.rakshaksetu.app.service.AnalysisService
import com.rakshaksetu.app.service.PendingCallStore
import java.util.UUID

/**
 * Durable per-call state surviving process death during an active phone call.
 * All writes are synchronous-commit so a HiOS/MIUI freezer kill cannot lose them.
 */
object CallStateTracker {
    private const val PREFS_NAME = "rakshak_call_state_tracker"
    private const val KEY_START_TIME = "key_start_time"
    private const val KEY_INCOMING_NUMBER = "key_incoming_number"
    private const val KEY_SAW_OFFHOOK = "key_saw_offhook"

    internal fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordCallStart(context: Context, timeMs: Long = System.currentTimeMillis()) {
        getPrefs(context).edit()
            .putLong(KEY_START_TIME, timeMs)
            .putBoolean(KEY_SAW_OFFHOOK, true)
            .commit()
    }

    fun recordIncomingNumber(context: Context, number: String?) {
        if (!number.isNullOrBlank()) {
            getPrefs(context).edit().putString(KEY_INCOMING_NUMBER, number).commit()
        }
    }

    fun getCallStartTime(context: Context): Long =
        getPrefs(context).getLong(KEY_START_TIME, 0L)

    fun getIncomingNumber(context: Context): String? =
        getPrefs(context).getString(KEY_INCOMING_NUMBER, null)

    /**
     * True only when a call actually reached OFFHOOK (answered or outgoing).
     * Guards against missed / rejected calls that still emit CALL_STATE_IDLE.
     */
    fun hasAnsweredCall(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_SAW_OFFHOOK, false)

    fun reset(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}

/**
 * Single idempotent entry point that both the manifest receiver and the runtime
 * telephony callback funnel into. Guarantees exactly one AnalysisService launch
 * per completed call even when Android 14/15 delivers duplicate IDLE transitions
 * (dual-SIM slots, OEM state machines, broadcast + callback racing).
 */
object AnalysisTrigger {
    private const val TAG = "AnalysisTrigger"
    private const val PREFS_NAME = "rakshak_analysis_trigger"
    private const val KEY_LAST_FINGERPRINT = "key_last_fingerprint"
    private const val KEY_LAST_TRIGGER_MS = "key_last_trigger_ms"

    const val DEDUP_WINDOW_MS = 5_000L
    const val MIN_ANALYZABLE_DURATION_SEC = 3

    fun maybeStartAnalysis(
        context: Context,
        fallbackDurationSec: Long = 10L,
        source: String = "unknown"
    ): Boolean {
        val ctx = context.applicationContext
        val endTime = System.currentTimeMillis()

        if (!CallStateTracker.hasAnsweredCall(ctx)) {
            Log.d(TAG, "[$source] No answered call (missed/rejected). Skipping analysis.")
            CallStateTracker.reset(ctx)
            return false
        }

        val startTime = CallStateTracker.getCallStartTime(ctx)
        val number = CallStateTracker.getIncomingNumber(ctx) ?: resolveLatestCallNumber(ctx)
        val durationSec = if (startTime > 0) {
            ((endTime - startTime) / 1000).coerceAtLeast(1)
        } else {
            fallbackDurationSec
        }

        val fingerprint = "${startTime}_${number ?: ""}"
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastFingerprint = prefs.getString(KEY_LAST_FINGERPRINT, null)
        val lastTriggerMs = prefs.getLong(KEY_LAST_TRIGGER_MS, 0L)

        if (fingerprint == lastFingerprint && endTime - lastTriggerMs < DEDUP_WINDOW_MS) {
            Log.d(TAG, "[$source] Duplicate IDLE suppressed within dedup window.")
            return false
        }
        if (endTime - lastTriggerMs < DEDUP_WINDOW_MS && durationSec < MIN_ANALYZABLE_DURATION_SEC) {
            Log.d(TAG, "[$source] Spurious short-IDLE burst suppressed.")
            return false
        }

        prefs.edit()
            .putString(KEY_LAST_FINGERPRINT, fingerprint)
            .putLong(KEY_LAST_TRIGGER_MS, endTime)
            .apply()

        val callId = UUID.randomUUID().toString()
        PendingCallStore.save(
            ctx,
            PendingCallStore.PendingCallRecord(
                callId = callId,
                phoneNumber = number ?: "Incoming Call",
                durationSec = durationSec.toInt(),
                endEpochMs = endTime
            )
        )

        var startedService = false
        try {
            val intent = Intent(ctx, AnalysisService::class.java).apply {
                putExtra(AnalysisService.EXTRA_CALL_ID, callId)
                putExtra(AnalysisService.EXTRA_PHONE_NUMBER, number ?: "Incoming Call")
                putExtra(AnalysisService.EXTRA_DURATION_SEC, durationSec.toInt())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
            startedService = true
            Log.i(TAG, "[$source] AnalysisService triggered for callId=$callId (duration=${durationSec}s)")
        } catch (e: Exception) {
            Log.e(TAG, "[$source] Direct start of AnalysisService failed (Android 14/15 background restriction); using WorkManager fallback", e)
        }

        if (!startedService) {
            // Immediate JobScheduler execution bypasses FGS background start restrictions on Android 14/15
            com.rakshaksetu.app.service.CallAnalysisWorker.enqueue(
                ctx,
                callId,
                number ?: "Incoming Call",
                durationSec.toInt()
            )
        }
        CallStateTracker.reset(ctx)
        return true
    }

    private fun resolveLatestCallNumber(context: Context): String? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALL_LOG
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            val cursor = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.NUMBER),
                null,
                null,
                "${android.provider.CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    it.getString(it.getColumnIndexOrThrow(android.provider.CallLog.Calls.NUMBER))
                } else null
            }
        } catch (e: Exception) {
            Log.d(TAG, "CallLog query fallback failed: ${e.message}")
            null
        }
    }
}

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

            when (stateStr) {
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    AnalysisTrigger.maybeStartAnalysis(context, source = "receiver")
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    CallStateTracker.recordCallStart(context)
                }
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    CallStateTracker.recordIncomingNumber(context, number)
                    if (!number.isNullOrBlank()) {
                        PreCallWarningDispatcher.onRinging(context, number)
                    }
                }
            }
        }
    }
}

class RakshakCallStateListener(private val context: Context) {

    private var telephonyManager: TelephonyManager? = null

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private var telephonyCallback: TelephonyCallback? = null

    private var phoneStateListener: PhoneStateListener? = null

    companion object {
        private var instance: RakshakCallStateListener? = null

        @Synchronized
        fun register(context: Context) {
            if (instance == null) {
                instance = RakshakCallStateListener(context.applicationContext)
                instance?.startListening()
                Log.i("RakshakCallStateListener", "Registered call state listener successfully.")
            }
        }

        @Synchronized
        fun unregister(context: Context) {
            instance?.stopListening()
            instance = null
        }
    }

    private fun startListening() {
        telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleStateChange(state, null)
                }
            }
            try {
                telephonyManager?.registerTelephonyCallback(
                    context.mainExecutor,
                    telephonyCallback as TelephonyCallback
                )
            } catch (e: Exception) {
                Log.e("RakshakCallStateListener", "registerTelephonyCallback failed", e)
            }
        } else {
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleStateChange(state, phoneNumber)
                }
            }
            try {
                telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            } catch (e: Exception) {
                Log.e("RakshakCallStateListener", "listen failed", e)
            }
        }
    }

    private fun stopListening() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback?.let {
                    telephonyManager?.unregisterTelephonyCallback(it as TelephonyCallback)
                }
            } else {
                phoneStateListener?.let {
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            Log.d("RakshakCallStateListener", "unregister failed: ${e.message}")
        }
    }

    private fun handleStateChange(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                AnalysisTrigger.maybeStartAnalysis(context, source = "listener")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d("RakshakCallStateListener", "Call OFFHOOK (call connected).")
                CallStateTracker.recordCallStart(context)
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("RakshakCallStateListener", "Call RINGING: $phoneNumber")
                CallStateTracker.recordIncomingNumber(context, phoneNumber)
                if (!phoneNumber.isNullOrBlank()) {
                    PreCallWarningDispatcher.onRinging(context, phoneNumber)
                }
            }
        }
    }
}
