package com.rakshaksetu.voip.telecom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.rakshaksetu.voip.MainActivity
import com.rakshaksetu.voip.R

/**
 * Manages modern NotificationCompat.CallStyle notifications for Android 14 VoIP calls.
 */
class IncomingCallNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_INCOMING_ID = "voip_incoming_channel"
        const val CHANNEL_ONGOING_ID = "voip_ongoing_channel"
        const val NOTIFICATION_ID_CALL = 1001

        const val ACTION_ANSWER = "com.rakshaksetu.voip.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.rakshaksetu.voip.ACTION_DECLINE"
        const val ACTION_HANGUP = "com.rakshaksetu.voip.ACTION_HANGUP"
        const val EXTRA_CALLER_ID = "extra_caller_id"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Incoming ringing channel (High importance)
            val incomingChannel = NotificationChannel(
                CHANNEL_INCOMING_ID,
                context.getString(R.string.channel_incoming_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_incoming_desc)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // Ongoing active call channel
            val ongoingChannel = NotificationChannel(
                CHANNEL_ONGOING_ID,
                context.getString(R.string.channel_call_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_call_desc)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(incomingChannel)
            notificationManager.createNotificationChannel(ongoingChannel)
        }
    }

    /**
     * Builds NotificationCompat.CallStyle notification for incoming ringing call.
     */
    fun buildIncomingCallNotification(callerId: String): Notification {
        val caller = Person.Builder()
            .setName(callerId)
            .setImportant(true)
            .build()

        // Full-screen activity intent when phone is locked
        val fullScreenIntent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            putExtra(EXTRA_CALLER_ID, callerId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val answerIntent = Intent(context, RakshakConnectionService::class.java).apply {
            action = ACTION_ANSWER
            putExtra(EXTRA_CALLER_ID, callerId)
        }
        val answerPendingIntent = PendingIntent.getService(
            context,
            1,
            answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(context, RakshakConnectionService::class.java).apply {
            action = ACTION_DECLINE
            putExtra(EXTRA_CALLER_ID, callerId)
        }
        val declinePendingIntent = PendingIntent.getService(
            context,
            2,
            declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_INCOMING_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming Encrypted Call")
            .setContentText(callerId)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller,
                    declinePendingIntent,
                    answerPendingIntent
                )
            )
            .build()
    }

    /**
     * Builds NotificationCompat.CallStyle notification for ongoing active call.
     */
    fun buildOngoingCallNotification(peerId: String): Notification {
        val caller = Person.Builder()
            .setName(peerId)
            .setImportant(true)
            .build()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(context, RakshakConnectionService::class.java).apply {
            action = ACTION_HANGUP
        }
        val hangupPendingIntent = PendingIntent.getService(
            context,
            3,
            hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ONGOING_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Rakshak Encrypted VoIP Active")
            .setContentText("Sovereign DTLS-SRTP Audio Tap Active")
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setStyle(
                NotificationCompat.CallStyle.forOngoingCall(
                    caller,
                    hangupPendingIntent
                )
            )
            .build()
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID_CALL)
    }
}
