package com.rakshaksetu.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.service.AnalysisService
import com.rakshaksetu.app.service.BatteryOptimizationHelper
import com.rakshaksetu.app.telephony.RakshakCallStateListener
import java.util.UUID

/**
 * Bootstrap activity. Handles:
 * 1. Runtime permission requests (T2)
 * 2. Battery optimization exemption (T7)
 * 3. Telephony listener registration
 * 4. Debug: trigger fake scam analysis
 * 
 * This is a MINIMAL bootstrap — full Compose UI (S0-S12) comes later.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            onPermissionsGranted()
        } else {
            val denied = results.filter { !it.value }.keys
            Log.w(TAG, "Permissions denied: $denied")
            Toast.makeText(this, "Permissions required for scam detection", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Minimal UI — just request permissions and register listener
        // Full Compose UI (screens S0-S12) will be built by Frontend Engineer
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE
        )
        
        // POST_NOTIFICATIONS required on API 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // READ_MEDIA_AUDIO on API 33+, READ_EXTERNAL_STORAGE on older
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isEmpty()) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun onPermissionsGranted() {
        Log.d(TAG, "Setting up shield...")
        
        // Register telephony listener for call-end detection
        RakshakCallStateListener.register(applicationContext)
        Log.d(TAG, "Telephony listener registered")
        
        // T7: Request battery optimization exemption
        if (!BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) {
            BatteryOptimizationHelper.requestBatteryOptimizationExemption(this)
        }
        
        // T3: Check full-screen intent permission on API 34+
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                try {
                    startActivity(Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    ))
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open full-screen intent settings", e)
                }
            }
        }
        
        Toast.makeText(this, "Shield Active — monitoring calls", Toast.LENGTH_LONG).show()
        
        // DEBUG: Auto-trigger fake scam for testing
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "DEBUG: Triggering fake scam analysis in 3s")
            window.decorView.postDelayed({
                triggerFakeScamAnalysis()
            }, 3000)
        }
    }

    private fun triggerFakeScamAnalysis() {
        val intent = Intent(this, AnalysisService::class.java).apply {
            putExtra(AnalysisService.EXTRA_CALL_ID, UUID.randomUUID().toString())
            putExtra(AnalysisService.EXTRA_PHONE_NUMBER, "+919876543210")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
