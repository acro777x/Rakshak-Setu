package com.rakshaksetu.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.rakshaksetu.app.model.DetectionResult

class ScamAlertManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "scam_alert"
        const val ACTION_NOT_SCAM = "com.rakshaksetu.app.ACTION_NOT_SCAM"
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun showScamAlert(result: DetectionResult) {
        if (!result.isScam || result.confidence < 0.60f) {
            return
        }

        val isHighConfidence = result.confidence >= 0.80f
        val notificationId = result.callId.hashCode()

        val title = if (isHighConfidence) "⚠️ SCAM LIKELY: ${result.scamType}" else "Potential Scam Call Detected"
        val content = "Do NOT transfer money or share OTP"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildSummary(result)))
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

        val maskedPhone = if (result.phoneNumber.length >= 4) "+91XXXXXX${result.phoneNumber.takeLast(4)}" else "+91XXXXXX0000"
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Do NOT transfer money or share OTP. Caller: $maskedPhone")
            .build()
            
        builder.setPublicVersion(publicNotification)

        if (isHighConfidence) {
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .color = android.graphics.Color.RED
                
            var canUseFullScreen = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                canUseFullScreen = notificationManager.canUseFullScreenIntent()
            }
            if (canUseFullScreen) {
                builder.setFullScreenIntent(evidencePendingIntent(result.callId), true)
            } else {
                builder.setContentIntent(evidencePendingIntent(result.callId))
            }
                
            builder.addAction(
                android.R.drawable.ic_menu_call,
                "Call 1930",
                helplinePendingIntent()
            )
            builder.addAction(
                android.R.drawable.ic_menu_view,
                "View Evidence",
                evidencePendingIntent(result.callId)
            )
            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Not a Scam",
                feedbackPendingIntent(result.callId)
            )
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .color = android.graphics.Color.YELLOW
        }

        notificationManager.notify(notificationId, builder.build())
    }

    fun buildSummary(result: DetectionResult): String {
        val durationStr = "${result.durationSec}s"
        val flaggedPhrases = result.flaggedSegments.joinToString(", ") { it.text }
        return "Do NOT transfer money or share OTP\n" +
               "Duration: $durationStr\n" +
               "Flagged Phrases: $flaggedPhrases"
    }

    private fun helplinePendingIntent(): PendingIntent {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
        return PendingIntent.getActivity(
            context,
            1930,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun evidencePendingIntent(callId: String): PendingIntent {
        val intent = Intent(context, com.rakshaksetu.app.ui.EvidenceActivity::class.java).apply {
            putExtra(EXTRA_CALL_ID, callId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun feedbackPendingIntent(callId: String): PendingIntent {
        val intent = Intent(ACTION_NOT_SCAM).apply {
            putExtra(EXTRA_CALL_ID, callId)
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for detected scam calls"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
