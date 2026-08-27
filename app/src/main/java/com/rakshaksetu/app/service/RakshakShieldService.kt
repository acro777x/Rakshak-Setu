package com.rakshaksetu.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rakshaksetu.app.MainActivity
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.telephony.RakshakCallStateListener

/**
 * 24/7 Persistent Background Shield Service.
 *
 * Keeps Rakshak Setu's AI call protection, call-state listener, and
 * OEM anti-kill watchdog active in the background even after the user
 * closes the app or swipes it away from Recent Tasks.
 */
class RakshakShieldService : Service() {

    companion object {
        private const val TAG = "RakshakShieldService"
        const val CHANNEL_ID = "shield_status"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.rakshaksetu.app.ACTION_STOP_SHIELD"

        fun start(context: Context) {
            val consent = ConsentStore(context)
            if (!consent.isShieldActive) {
                Log.d(TAG, "Shield consent inactive, skipping service start.")
                return
            }
            val intent = Intent(context, RakshakShieldService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.i(TAG, "RakshakShieldService start requested.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start RakshakShieldService (background restriction?)", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RakshakShieldService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to send stop action to RakshakShieldService", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "RakshakShieldService created.")
        ensureChannel()
        startInForeground()
        RakshakCallStateListener.register(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "Stop action received, shutting down shield.")
            RakshakCallStateListener.unregister(this)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Verify consent is still active
        if (!ConsentStore(this).isShieldActive) {
            Log.w(TAG, "Consent is disabled; stopping shield service.")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startInForeground()
        RakshakCallStateListener.register(this)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "RakshakShieldService destroyed.")
        // If shield is still configured active, restart to survive OEM task kills
        if (ConsentStore(this).isShieldActive) {
            Log.i(TAG, "Shield still active, requesting restart.")
            start(applicationContext)
        }
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentTitle("🛡️ Rakshak Setu Active")
            .setContentText("AI call protection is actively safeguarding your phone")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setColor(android.graphics.Color.parseColor("#1B5E20")) // Forest green
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Shield Status",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows continuous protection status of Rakshak Setu"
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
