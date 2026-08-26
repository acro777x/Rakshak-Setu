package com.rakshaksetu.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.rakshaksetu.app.community.BlacklistRepository
import com.rakshaksetu.app.community.CommunityUploadWorker
import com.rakshaksetu.app.community.PreCallWarningDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RakshakSetuApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        try {
            com.rakshaksetu.app.telephony.RakshakCallStateListener.register(this)
            CommunityUploadWorker.schedule(this)
            // Federated Learning threshold persistence (no-op until attached)
            com.rakshaksetu.app.pipeline.FederatedLearningManager.attach(this)
            // Blacklist seed on first launch (Room source of truth)
            appScope.launch {
                BlacklistRepository(this@RakshakSetuApp).ensureSeeded()
            }
            // OEM swipe-up/force-stop kill recovery: resume any interrupted analysis
            com.rakshaksetu.app.service.AnalysisService.resumeIfPending(this)
        } catch (e: Exception) {
            // Never crash the shield on bootstrap issues
            android.util.Log.e("RakshakSetuApp", "Bootstrap failure contained", e)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val scamChannel = NotificationChannel(
                "scam_alert",
                "Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scam detection alerts"
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                enableVibration(true)
            }

            val shieldChannel = NotificationChannel(
                "shield_status",
                "Shield Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shield monitoring status"
            }

            val diagnosticChannel = NotificationChannel(
                "analysis_diagnostics",
                "Analysis Diagnostics",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Status messages when an analysis could not run"
            }

            val precallChannel = NotificationChannel(
                PreCallWarningDispatcher.CHANNEL_ID,
                "Pre-Call Warnings",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up warnings for known scam callers while the phone rings"
            }

            notificationManager.createNotificationChannel(scamChannel)
            notificationManager.createNotificationChannel(shieldChannel)
            notificationManager.createNotificationChannel(diagnosticChannel)
            notificationManager.createNotificationChannel(precallChannel)
        }
    }
}
