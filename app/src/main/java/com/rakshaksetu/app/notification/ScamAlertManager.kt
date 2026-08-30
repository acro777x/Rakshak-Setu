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

        /** Shared masking utility for notifications/UI (keeps last 4 digits visible). */
        fun maskNumberForDisplay(raw: String): String {
            val digits = raw.filter { it.isDigit() }
            if (digits.length < 4) return "+91XXXXXX0000"
            return "+91XXXXXX${digits.takeLast(4)}"
        }
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    fun showScamAlert(result: DetectionResult) {
        if (!result.isScam || result.confidence < 0.60f) {
            return
        }

        val isVoiceClone = result.scamType == "ai_voice_kidnap" || result.scamType?.contains("voice") == true || result.scamType?.contains("clone") == true
        val categoryTitle = getCategoryDisplayName(result.scamType)
        val confPercent = (result.confidence * 100).toInt().coerceIn(60, 99)
        val notificationId = result.callId.hashCode()
        val maskedPhone = maskNumberForDisplay(result.phoneNumber)

        val (title, content, alertColor) = if (isVoiceClone) {
            Triple(
                "⚠️ Voice Clone Warning: Artificial Speech Detected",
                "The caller's voice sounded AI-generated. Be cautious if they demanded money or claimed an emergency.",
                android.graphics.Color.parseColor("#880E4F") // Deep burgundy for deepfakes
            )
        } else {
            Triple(
                "⚠️ Warning: Suspected $categoryTitle",
                "This caller may be impersonating officials. Do not share OTP, PIN, or transfer funds.",
                android.graphics.Color.parseColor("#C62828") // Strong crimson
            )
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildSummary(result, categoryTitle, confPercent, isVoiceClone)))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(alertColor)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))

        val publicNotification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText("Security alert for call from $maskedPhone. Do not transfer funds.")
            .setColor(alertColor)
            .build()
            
        builder.setPublicVersion(publicNotification)

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

        notificationManager.notify(notificationId, builder.build())
    }

    fun showSafeCallNotification(phoneNumber: String) {
        val maskedPhone = maskNumberForDisplay(phoneNumber)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🛡️ Call Verified Safe")
            .setContentText("No suspicious patterns or voice cloning detected from $maskedPhone.")
            .setColor(android.graphics.Color.parseColor("#2E7D32")) // Calming green
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setTimeoutAfter(5000) // Auto-dismiss in 5s
            .build()

        notificationManager.notify(phoneNumber.hashCode(), builder)
    }

    fun buildSummary(
        result: DetectionResult,
        categoryTitle: String = getCategoryDisplayName(result.scamType),
        confPercent: Int = (result.confidence * 100).toInt(),
        isVoiceClone: Boolean = false
    ): String {
        val durationStr = "${result.durationSec}s"
        val flaggedPhrases = if (result.flaggedSegments.isNotEmpty()) {
            result.flaggedSegments.joinToString("\n• ") { "\"${it.text}\"" }
        } else if (isVoiceClone) {
            "Spectral acoustic anomalies characteristic of AI voice synthesis detected."
        } else {
            "Behavioral urgency and extraction patterns detected in conversation."
        }

        return if (isVoiceClone) {
            "⚠️ Alert: AI Voice Cloning Impersonation\n" +
            "🛑 Advice: Verify caller identity through another channel. Do not send emergency money.\n" +
            "⏱️ Call Duration: $durationStr\n" +
            "🔍 Key Findings:\n• $flaggedPhrases"
        } else {
            "⚠️ Suspected Threat: $categoryTitle\n" +
            "🛑 Advice: Disconnect. Never share OTP or transfer funds to verify an account.\n" +
            "⏱️ Call Duration: $durationStr\n" +
            "🔍 Flagged Speech:\n• $flaggedPhrases"
        }
    }

    fun getCategoryDisplayName(scamType: String?): String = when (scamType) {
        "digital_arrest" -> "CBI / Police Digital Arrest"
        "ai_voice_kidnap" -> "AI Voice Cloning / Kidnapping"
        "screen_share_scam" -> "Screen Share / Remote Access"
        "loan_extortion" -> "Loan App / Morphed Photo Threat"
        "kyc_fraud" -> "Bank KYC / Account Freeze / OTP"
        "esim_swap_5g" -> "5G Upgrade / eSIM Swap Fraud"
        "govt_subsidy_phishing" -> "PM Kisan / Govt Subsidy Scam"
        "courier_customs" -> "Customs / Narcotics Parcel Seizure"
        "electricity_bill" -> "Electricity Power Cut Fraud"
        "trai_sim_block" -> "TRAI / SIM Card Block Scam"
        "loan_lottery" -> "Loan / KBC / Lottery Fraud"
        "job_task_scam" -> "Work-From-Home / Rating Task Fraud"
        "upi_qr_scam" -> "UPI / QR Request Money Scam"
        "sextortion_blackmail" -> "Sextortion / Video Call Blackmail"
        "crypto_investment" -> "Stock Market / Crypto VIP Group"
        "traffic_challan" -> "Fake Traffic E-Challan Penalty"
        "pension_epfo" -> "Pension / EPFO Life Certificate"
        "gas_subsidy" -> "LPG Gas Subsidy / Biometric Lock"
        "matrimonial_romance" -> "Matrimonial / Romance Gift Scam"
        "customer_care_poisoning" -> "Fake Customer Care / Refund Scam"
        "emerging_threats" -> "Emerging Telecom Cyber Threat"
        else -> scamType?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: "Cyber Telecom Threat"
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
        val intent = Intent(context, com.rakshaksetu.app.MainActivity::class.java).apply {
            putExtra(EXTRA_CALL_ID, callId)
            putExtra("NAV_ROUTE", "red_alert")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
