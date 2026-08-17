package com.rakshaksetu.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class RakshakSetuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        try {
            com.rakshaksetu.app.community.CommunityUploadWorker.schedule(this)
        } catch (e: Exception) {
            // Ignore error
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

            notificationManager.createNotificationChannel(scamChannel)
            notificationManager.createNotificationChannel(shieldChannel)
        }
    }
}
