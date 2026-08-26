package com.rakshaksetu.app.pipeline

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * Detects device hardware capabilities at runtime and selects
 * the appropriate AI tier for model loading and inference.
 *
 * LITE (≤4GB RAM): Vosk ASR + AASIST-L deepfake + MiniLM intent
 * FULL (>4GB RAM): Sherpa-ONNX IndicConformer + AASIST + MiniLM intent
 */
object DeviceCapabilityManager {
    private const val TAG = "DeviceCapability"
    private const val RAM_THRESHOLD_GB = 4.0

    enum class AiTier {
        /** Budget phones ≤4GB: lighter models, lower accuracy */
        LITE,
        /** Mid-range+ >4GB: full models, maximum accuracy */
        FULL
    }

    private var cachedTier: AiTier? = null

    fun detectTier(context: Context): AiTier {
        cachedTier?.let { return it }

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGB = memInfo.totalMem / (1024.0 * 1024 * 1024)

        val tier = if (totalRamGB > RAM_THRESHOLD_GB) AiTier.FULL else AiTier.LITE
        cachedTier = tier

        Log.i(TAG, "Device RAM: %.1f GB → AI Tier: %s".format(totalRamGB, tier.name))
        return tier
    }

    fun getTotalRamGB(context: Context): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        return mem.totalMem / (1024.0 * 1024 * 1024)
    }

    fun getAvailableRamMB(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        return mem.availMem / (1024 * 1024)
    }
}
