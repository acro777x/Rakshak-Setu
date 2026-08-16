package com.rakshaksetu.app.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.rakshaksetu.app.service.AnalysisService
import java.util.UUID

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
                    CallStateTracker.callStartTime = System.currentTimeMillis()
                }
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    CallStateTracker.incomingNumber = number
                }
            }
        }
    }
    
    private fun handleCallEnd(context: Context) {
        val endTime = System.currentTimeMillis()
        val startTime = CallStateTracker.callStartTime
        if (startTime > 0) {
            val durationSec = (endTime - startTime) / 1000
            if (durationSec >= 10) {
                val serviceIntent = Intent(context, AnalysisService::class.java).apply {
                    putExtra(AnalysisService.EXTRA_CALL_ID, UUID.randomUUID().toString())
                    putExtra(AnalysisService.EXTRA_PHONE_NUMBER, CallStateTracker.incomingNumber ?: "Unknown")
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
        CallStateTracker.reset()
    }
}

object CallStateTracker {
    var callStartTime: Long = 0L
    var incomingNumber: String? = null
    
    fun reset() {
        callStartTime = 0L
        incomingNumber = null
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
                val startTime = CallStateTracker.callStartTime
                if (startTime > 0) {
                    val durationSec = (endTime - startTime) / 1000
                    if (durationSec >= 10) {
                        val serviceIntent = Intent(context, AnalysisService::class.java).apply {
                            putExtra(AnalysisService.EXTRA_CALL_ID, UUID.randomUUID().toString())
                            putExtra(AnalysisService.EXTRA_PHONE_NUMBER, CallStateTracker.incomingNumber ?: phoneNumber ?: "Unknown")
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(serviceIntent)
                        } else {
                            context.startService(serviceIntent)
                        }
                    }
                }
                CallStateTracker.reset()
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                CallStateTracker.callStartTime = System.currentTimeMillis()
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                if (phoneNumber != null) {
                    CallStateTracker.incomingNumber = phoneNumber
                }
            }
        }
    }
}
