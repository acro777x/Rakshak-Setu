package com.rakshaksetu.voip.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/**
 * Foreground Service for active VoIP phone calls with Android 14 FOREGROUND_SERVICE_TYPE_PHONE_CALL.
 */
open class CallForegroundService : Service() {

    companion object {
        private const val TAG = "CallForegroundService"
        const val ACTION_START_CALL = "com.rakshaksetu.voip.START_CALL"
        const val ACTION_STOP_CALL = "com.rakshaksetu.voip.STOP_CALL"
        const val EXTRA_PEER_ID = "extra_peer_id"

        fun startService(context: Context, peerId: String) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_START_CALL
                putExtra(EXTRA_PEER_ID, peerId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CallForegroundService::class.java).apply {
                action = ACTION_STOP_CALL
            }
            context.stopService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: IncomingCallNotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = IncomingCallNotificationManager(this)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "RakshakVoip::ActiveCallWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_CALL -> {
                val peerId = intent.getStringExtra(EXTRA_PEER_ID) ?: "Encrypted Peer"
                val notification = notificationManager.buildOngoingCallNotification(peerId)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        IncomingCallNotificationManager.NOTIFICATION_ID_CALL,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(IncomingCallNotificationManager.NOTIFICATION_ID_CALL, notification)
                }

                wakeLock?.acquire(3 * 60 * 60 * 1000L) // 3 hour safety timeout
                Log.i(TAG, "Started foreground phone call service for $peerId")
            }
            ACTION_STOP_CALL -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        notificationManager.cancelNotification()
        Log.i(TAG, "CallForegroundService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
