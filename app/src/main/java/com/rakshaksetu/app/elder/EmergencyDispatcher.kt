package com.rakshaksetu.app.elder

import android.content.Context
import android.util.Log
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.security.CallIdValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Emergency family dispatcher (M3).
 *
 * Two activation paths:
 *  1. One-tap: "Alert Family" button fires [com.rakshaksetu.app.receiver.ElderAlertReceiver]
 *     -> [dispatch]. Available whenever a scam verdict exists.
 *  2. Opt-in auto-send: invoked from AnalysisService only when the user enabled
 *     automatic dispatch AND the verdict clears [ElderModeStore.AUTO_SEND_CONFIDENCE_THRESHOLD].
 *
 * Delivery chain per guardian: direct device SMS first (works offline — critical
 * during emergencies), then the user-configured HTTP gateway when direct queuing
 * fails or is filtered. Safety rails: <=3 guardians, per-guardian 6h rate limit,
 * masked victim number, multipart SMS.
 */
object EmergencyDispatcher {
    private const val TAG = "EmergencyDispatcher"
    private const val MAX_SMS_BODY_CHARS = 480

    /** One-tap eligibility: any scam verdict the user is looking at. */
    fun qualifiesForOneTap(result: DetectionResult): Boolean =
        result.isScam && result.confidence >= 0.60f

    /** Auto-send eligibility: high-confidence scam only. */
    fun qualifiesForAutoSend(result: DetectionResult): Boolean =
        result.isScam &&
            result.confidence >= ElderModeStore.AUTO_SEND_CONFIDENCE_THRESHOLD &&
            CallIdValidator.isValid(result.callId)

    fun buildSmsBody(result: DetectionResult): String {
        val maskedCaller = ScamAlertManager.maskNumberForDisplay(result.phoneNumber)
        val scamLabel = (result.scamType ?: "scam").replace('_', ' ').uppercase()
        val timeStr = java.text.SimpleDateFormat(
            "dd MMM, hh:mm a", java.util.Locale.US
        ).format(java.util.Date(result.callEndEpoch))

        val body = buildString {
            append("EMERGENCY: Possible $scamLabel call detected on my phone.")
            append(" Caller: $maskedCaller at $timeStr.")
            append(" Confidence ${(result.confidence * 100).toInt()}%. ")
            append("Please call me to confirm I am safe. Do not transfer money on my behalf.")
        }
        return if (body.length > MAX_SMS_BODY_CHARS) body.substring(0, MAX_SMS_BODY_CHARS) else body
    }

    /**
     * Sends the emergency SMS to all configured guardians via the transport chain.
     * @return number of guardians for which at least one transport accepted delivery.
     */
    suspend fun dispatch(context: Context, result: DetectionResult, autoTriggered: Boolean): Int =
        withContext(Dispatchers.IO) {
            dispatchBlocking(context.applicationContext, result, autoTriggered)
        }

    /** Synchronous variant for broadcast receivers (non-suspend call sites). */
    fun dispatchBlocking(context: Context, result: DetectionResult, autoTriggered: Boolean): Int {
        val appContext = context.applicationContext
        val store = ElderModeStore(appContext)
        if (!store.isEnabled) return 0

        val guardians = store.getGuardians()
        if (guardians.isEmpty()) {
            Log.w(TAG, "Elder Mode on but no guardians configured; dispatch skipped.")
            return 0
        }

        val body = buildSmsBody(result)
        val now = System.currentTimeMillis()
        var deliveredCount = 0

        val gatewayUrl = store.smsGatewayUrl

        guardians.forEach { guardian ->
            val last = store.lastSmsEpochMs(guardian.number)
            if (now - last < ElderModeStore.PER_GUARDIAN_RATE_LIMIT_MS) {
                Log.d(TAG, "Rate limit active for guardian ${guardian.name}; skipping.")
                return@forEach
            }

            var delivered = false

            // Tier 1: direct device SMS (offline-capable)
            try {
                val direct = DirectSmsTransport(appContext)
                delivered = kotlinx.coroutines.runBlocking { direct.send(guardian.number, body) }
            } catch (e: Exception) {
                Log.e(TAG, "Direct transport threw for ${guardian.name}", e)
            }

            // Tier 2: HTTP gateway fallback (user-provided free/provider API)
            if (!delivered && gatewayUrl.isNotBlank()) {
                try {
                    val gateway = HttpGatewayTransport(gatewayUrl)
                    delivered = kotlinx.coroutines.runBlocking { gateway.send(guardian.number, body) }
                    if (delivered) Log.i(TAG, "Guardian ${guardian.name} alerted via HTTP gateway.")
                } catch (e: Exception) {
                    Log.e(TAG, "Gateway transport failed for ${guardian.name}", e)
                }
            }

            if (delivered) {
                store.markSmsSent(guardian.number, now)
                deliveredCount++
                Log.i(TAG, "Emergency alert delivered to ${guardian.name} (auto=$autoTriggered).")
            } else {
                Log.w(TAG, "All transports failed for ${guardian.name}; not rate-limiting retry.")
            }
        }

        return deliveredCount
    }
}
