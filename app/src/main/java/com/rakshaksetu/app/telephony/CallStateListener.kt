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
import com.rakshaksetu.app.service.AnalysisService
import java.util.UUID

/**
 * Persistent CallStateTracker surviving process death during active phone calls.
 */
object CallStateTracker {
    private const val PREFS_NAME = "rakshak_call_state_tracker"
    private const val KEY_START_TIME = "key_start_time"
    private const val KEY_INCOMING_NUMBER = "key_incoming_number"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordCallStart(context: Context, timeMs: Long = System.currentTimeMillis()) {
        getPrefs(context).edit().putLong(KEY_START_TIME, timeMs).apply()
    }

    fun recordIncomingNumber(context: Context, number: String?) {
        if (!number.isNullOrBlank()) {
            getPrefs(context).edit().putString(KEY_INCOMING_NUMBER, number).apply()
        }
    }

    fun getCallStartTime(context: Context): Long =
        getPrefs(context).getLong(KEY_START_TIME, 0L)

    fun getIncomingNumber(context: Context): String? =
        getPrefs(context).getString(KEY_INCOMING_NUMBER, null)

    fun reset(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            when (stateStr) {
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    handleCallEnd(context)
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                    CallStateTracker.recordCallStart(context)
                }
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    CallStateTracker.recordIncomingNumber(context, number)
                }
            }
        }
    }
    
    private fun handleCallEnd(context: Context) {
        val endTime = System.currentTimeMillis()
        val startTime = CallStateTracker.getCallStartTime(context)
        val incomingNumber = CallStateTracker.getIncomingNumber(context)

        val durationSec = if (startTime > 0) {
            ((endTime - startTime) / 1000).coerceAtLeast(1)
        } else {
            10L
        }

        Log.i("CallStateReceiver", "Call ended (duration=${durationSec}s). Triggering AnalysisService.")
        val serviceIntent = Intent(context, AnalysisService::class.java).apply {
            putExtra(AnalysisService.EXTRA_CALL_ID, UUID.randomUUID().toString())
            putExtra(AnalysisService.EXTRA_PHONE_NUMBER, incomingNumber ?: "Incoming Call")
            putExtra(AnalysisService.EXTRA_DURATION_SEC, durationSec.toInt())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
        CallStateTracker.reset(context)
    }
}

class RakshakCallStateListener(private val context: Context) {
    
    private var telephonyManager: TelephonyManager? = null
    
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private var telephonyCallback: TelephonyCallback? = null
    
    private var phoneStateListener: PhoneStateListener? = null
    
    companion object {
        private var instance: RakshakCallStateListener? = null
        
        fun register(context: Context) {
            if (instance == null) {
                instance = RakshakCallStateListener(context.applicationContext)
                instance?.startListening()
                Log.i("RakshakCallStateListener", "Registered call state listener successfully.")
            }
        }
        
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
            telephonyManager?.registerTelephonyCallback(
                context.mainExecutor,
                telephonyCallback as TelephonyCallback
            )
        } else {
            phoneStateListener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleStateChange(state, phoneNumber)
                }
            }
            telephonyManager?.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
        }
    }
    
    private fun stopListening() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyCallback?.let {
                telephonyManager?.unregisterTelephonyCallback(it as TelephonyCallback)
            }
        } else {
            phoneStateListener?.let {
                telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
            }
        }
    }
    
    private fun handleStateChange(state: Int, phoneNumber: String?) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                val endTime = System.currentTimeMillis()
                val startTime = CallStateTracker.getCallStartTime(context)
                val incomingNumber = CallStateTracker.getIncomingNumber(context) ?: phoneNumber

                val durationSec = if (startTime > 0) {
                    ((endTime - startTime) / 1000).coerceAtLeast(1)
                } else {
                    10L
                }

                Log.i("RakshakCallStateListener", "Call ended (duration=${durationSec}s). Triggering AnalysisService.")
                val serviceIntent = Intent(context, AnalysisService::class.java).apply {
                    putExtra(AnalysisService.EXTRA_CALL_ID, UUID.randomUUID().toString())
                    putExtra(AnalysisService.EXTRA_PHONE_NUMBER, incomingNumber ?: "Incoming Call")
                    putExtra(AnalysisService.EXTRA_DURATION_SEC, durationSec.toInt())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                CallStateTracker.reset(context)
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.d("RakshakCallStateListener", "Call OFFHOOK (call connected).")
                CallStateTracker.recordCallStart(context)
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d("RakshakCallStateListener", "Call RINGING: $phoneNumber")
                if (phoneNumber != null) {
                    CallStateTracker.recordIncomingNumber(context, phoneNumber)
                }
            }
        }
    }
}
