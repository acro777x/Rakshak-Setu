package com.rakshaksetu.app.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Multi-OEM Battery Optimization & Whitelisting Helper.
 * Handles Xiaomi (MIUI/HyperOS), Samsung (OneUI), Realme/Oppo (ColorOS),
 * Vivo (Funtouch), OnePlus (OxygenOS), Huawei (EMUI), and Tecno/Infinix (HiOS/XOS).
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptHelper"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Launches standard system dialog to request battery optimization exemption.
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
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback settings also failed", e2)
            }
        }
    }

    /**
     * Opens manufacturer-specific autostart/battery manager settings.
     */
    fun openManufacturerBatterySettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                Intent().setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                Intent("miui.intent.action.POWER_HIDE_MODE_APP_LIST")
            )
            manufacturer.contains("samsung") -> listOf(
                Intent().setClassName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                Intent().setClassName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.AppSleepListActivity")
            )
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> listOf(
                Intent().setClassName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                Intent().setClassName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity")
            )
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
            )
            manufacturer.contains("oneplus") -> listOf(
                Intent().setClassName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
            )
            manufacturer.contains("transsion") || manufacturer.contains("tecno") || manufacturer.contains("infinix") -> listOf(
                Intent().setClassName("com.transsion.phonemanager", "com.transsion.phonemanager.settings.AutoStartActivity"),
                Intent().setClassName("com.transsion.powercenter", "com.transsion.powercenter.PowerActivity")
            )
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> listOf(
                Intent().setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
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

        // Generic app details settings fallback
        try {
            val appInfoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(appInfoIntent)
        } catch (e: Exception) {
            Log.e(TAG, "App details fallback failed", e)
        }
    }

    /**
     * Returns the detected human-readable brand name.
     */
    fun getDetectedBrandName(): String {
        val m = Build.MANUFACTURER.lowercase()
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "Xiaomi (MIUI/HyperOS)"
            m.contains("samsung") -> "Samsung (OneUI)"
            m.contains("realme") -> "Realme (Realme UI)"
            m.contains("oppo") -> "Oppo (ColorOS)"
            m.contains("vivo") || m.contains("iqoo") -> "Vivo (Funtouch OS)"
            m.contains("oneplus") -> "OnePlus (OxygenOS)"
            m.contains("transsion") || m.contains("tecno") -> "Tecno (HiOS)"
            m.contains("infinix") -> "Infinix (XOS)"
            m.contains("huawei") || m.contains("honor") -> "Huawei (EMUI)"
            else -> Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Returns step-by-step instructions for the detected phone brand.
     */
    fun getOemInstructions(manufacturer: String = Build.MANUFACTURER.lowercase()): List<String> {
        return when {
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> listOf(
                "1. Tap 'Open Brand Settings' below.",
                "2. Set Battery Saver to 'No restrictions'.",
                "3. Enable 'Autostart' toggle for Rakshak Setu."
            )
            manufacturer.contains("samsung") -> listOf(
                "1. Open Settings → Battery & Device Care → Battery.",
                "2. Go to 'Background usage limits' → 'Never sleeping apps'.",
                "3. Tap '+' and add Rakshak Setu."
            )
            manufacturer.contains("realme") || manufacturer.contains("oppo") || manufacturer.contains("oneplus") -> listOf(
                "1. Open Settings → Battery → App battery management.",
                "2. Select Rakshak Setu → Allow background activity.",
                "3. Enable 'Allow auto-launch'."
            )
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> listOf(
                "1. Open Settings → Battery → High background power consumption.",
                "2. Enable toggle for Rakshak Setu.",
                "3. Set Battery Optimization to 'Don't optimize'."
            )
            manufacturer.contains("transsion") || manufacturer.contains("tecno") || manufacturer.contains("infinix") -> listOf(
                "1. Open Phone Master / Settings → Power Management.",
                "2. Select Rakshak Setu → Disable 'Auto-freeze'.",
                "3. Allow background execution."
            )
            else -> listOf(
                "1. Tap 'Grant Battery Whitelist' to exempt Rakshak Setu from OS battery killing.",
                "2. Select 'Unrestricted' in App Battery Settings."
            )
        }
    }
}
