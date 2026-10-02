package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Federated threshold-learning transport.
 *
 * The server half is deliberately a ~40-line coordinate-wise median rather than a
 * Flower/TFFL runtime: Flower's Android SDK is deprecated (0.0.2, Aug 2023) and
 * incompatible with current Flower releases, and its server would require a
 * separately hosted Python process that cannot ship inside a mobile prototype.
 * Coordinate-wise median is the same robust strategy Flower ships as
 * FedMedian / FedTrimmedAvg.
 *
 * PRIVACY CONTRACT:
 *  - No raw audio, no transcript, no phone number and NO device identifier leaves
 *    the device. The upload is a list of per-category threshold deltas.
 *  - Default-off: [isRemoteConfigured] is false until both an endpoint is set and
 *    consent is granted, so the app cannot phone home by accident.
 *  - Fully functional offline: with nothing configured both operations are no-ops
 *    and the app keeps exactly its current local-only behaviour.
 */
object FederatedThresholdSync {

    private const val TAG = "FL-Sync"
    private const val PREFS = "rakshak_fl_sync"
    private const val KEY_ENDPOINT = "key_fl_endpoint"
    private const val KEY_CONSENT = "key_fl_consent"
    private const val KEY_LAST_PULL_MS = "key_fl_last_pull_ms"
    private const val PULL_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6h
    private const val MAX_ENDPOINT_LEN = 512
    private const val MAX_CATEGORY_LEN = 64

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasConsent(context: Context): Boolean = prefs(context).getBoolean(KEY_CONSENT, false)

    fun setConsentGranted(context: Context, granted: Boolean) {
        prefs(context).edit().putBoolean(KEY_CONSENT, granted).apply()
        Log.i(TAG, "Federated threshold sharing consent = $granted")
    }

    /** Blank until explicitly configured; there is no built-in production endpoint. */
    fun endpoint(context: Context): String = prefs(context).getString(KEY_ENDPOINT, "").orEmpty()

    fun setEndpoint(context: Context, url: String) {
        val clean = url.trim().take(MAX_ENDPOINT_LEN)
        prefs(context).edit().putString(KEY_ENDPOINT, clean).apply()
        Log.i(TAG, "FL endpoint ${if (clean.isBlank()) "cleared" else "configured"}")
    }

    private fun isLocalhost(url: String): Boolean =
        url.startsWith("http://") &&
            (url.contains("10.0.2.2") || url.contains("127.0.0.1") || url.contains("localhost"))

    /**
     * True only when an endpoint is configured AND the user consented.
     * https is required for real deployments; plain http is tolerated solely for
     * an explicitly configured loopback demo server.
     */
    fun isRemoteConfigured(context: Context): Boolean {
        val ep = endpoint(context)
        return hasConsent(context) && ep.isNotBlank() &&
            (ep.startsWith("https://") || isLocalhost(ep))
    }

    // pushDeltas / pullGlobalThresholds / isPullDue / sync follow below.

    /**
     * Uploads locally-learned threshold deltas. Returns true only when the server
     * acknowledges, and only then are the staged deltas cleared -- a failed upload
     * leaves them staged so the next retry can still send them.
     */
    suspend fun pushDeltas(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured(context)) {
            Log.d(TAG, "Push skipped: no consented endpoint. Running local-only.")
            return@withContext false
        }
        val pending = FederatedLearningManager.pendingDeltaCount()
        if (pending == 0) {
            Log.d(TAG, "Push skipped: no local deltas to share.")
            return@withContext false
        }
        try {
            val payload = FederatedLearningManager.exportDeltas()
            val request = Request.Builder()
                .url(endpoint(context).trimEnd('/') + "/fl/deltas")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Push rejected: HTTP ${response.code}. Deltas retained.")
                    return@withContext false
                }
            }
            FederatedLearningManager.markDeltasExported()
            Log.i(TAG, "Push OK: $pending delta(s) shared (no identifiers attached).")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Push failed (${e.message}); deltas retained for retry.")
            false
        }
    }

    /** Fetches aggregated per-category thresholds and applies them. */
    suspend fun pullGlobalThresholds(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isRemoteConfigured(context)) return@withContext false
        try {
            val request = Request.Builder()
                .url(endpoint(context).trimEnd('/') + "/fl/thresholds")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Pull failed: HTTP ${response.code}")
                    return@withContext false
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@withContext false

                val obj = JSONObject(body).optJSONObject("thresholds") ?: return@withContext false
                val parsed = mutableMapOf<String, Float>()
                obj.keys().forEach { key ->
                    val v = obj.optDouble(key, Double.NaN)
                    if (key.isNotBlank() && key.length <= MAX_CATEGORY_LEN && !v.isNaN()) {
                        parsed[key] = v.toFloat()
                    }
                }
                if (parsed.isEmpty()) return@withContext false

                FederatedLearningManager.applyGlobalUpdate(parsed)
                prefs(context).edit().putLong(KEY_LAST_PULL_MS, System.currentTimeMillis()).apply()
                Log.i(TAG, "Pull OK: applied ${parsed.size} aggregated thresholds.")
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pull failed (${e.message}); keeping local thresholds.")
            false
        }
    }

    /** True when enough time has passed to warrant another pull. */
    fun isPullDue(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_PULL_MS, 0L)
        return System.currentTimeMillis() - last >= PULL_INTERVAL_MS
    }

    /**
     * One sync cycle: PULL first, then PUSH, so the device converges on the
     * community model before contributing. Never throws.
     */
    suspend fun sync(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val pulled = if (isPullDue(context)) pullGlobalThresholds(context) else false
            val pushed = pushDeltas(context)
            pulled || pushed
        } catch (e: Exception) {
            Log.w(TAG, "Sync cycle failed (${e.message}); local behaviour unaffected.")
            false
        }
    }
}