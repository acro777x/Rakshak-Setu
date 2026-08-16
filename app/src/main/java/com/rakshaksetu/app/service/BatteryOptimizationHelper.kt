package com.rakshaksetu.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * T7 — Xiaomi/Samsung/Realme kill ForegroundServices unless user whitelists.
 * This helper checks battery optimization status and guides user to whitelist.
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launches system dialog to request battery optimization exemption.
     * User must confirm. Cannot be auto-granted.
     */
    fun requestBatteryOptimizationExemption(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            Log.d(TAG, "Already exempted from battery optimization")
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open battery optimization settings", e)
            // Fallback: open general battery settings
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback also failed", e2)
            }
        }
    }

    /**
     * Opens manufacturer-specific autostart/battery manager settings.
     * Needed for Xiaomi MIUI, Samsung OneUI, Realme ColorOS, Oppo, Vivo.
     */
    fun openManufacturerBatterySettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> listOf(
                Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                Intent().setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            )
            manufacturer.contains("samsung") -> listOf(
                Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
            )
            manufacturer.contains("vivo") -> listOf(
                Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            )
            manufacturer.contains("oneplus") -> listOf(
                Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
            else -> emptyList()
        }

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                Log.d(TAG, "Manufacturer intent failed: ${intent.component}", e)
            }
        }
        Log.d(TAG, "No manufacturer-specific battery settings found for: $manufacturer")
    }
}
