package com.rakshaksetu.app.community

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.notification.ScamAlertManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Pre-call heads-up warning driven by CALL_STATE_RINGING.
 *
 * Design constraints honored:
 *  - Zero network: pure Room + deterministic CLI heuristics.
 *  - Zero ANR: assessment runs on a private single-thread executor, never the
 *    broadcast/main thread; results post a notification asynchronously.
 *  - Rate limited: at most one warning per normalized number per hour.
 *  - DPDP: fully suppressed when the user's shield consent is paused.
 */
object PreCallWarningDispatcher {
    private const val TAG = "PreCallWarning"
    const val CHANNEL_ID = "precall_warning"
    private const val RATE_LIMIT_MS = 60 * 60 * 1000L

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "rakshak-precall").apply { isDaemon = true }
    }
    private val lastWarnedAt = ConcurrentHashMap<String, Long>()

    fun onRinging(context: Context, rawNumber: String) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                if (!ConsentStore(appContext).isShieldActive) return@execute

                val repo = BlacklistRepository(appContext)
                val assessment = runBlocking {
                    withTimeout(4_000L) { repo.assess(rawNumber) }
                }

                if (assessment.risk.ordinal < RiskLevel.MEDIUM.ordinal) return@execute

                val now = System.currentTimeMillis()
                val last = lastWarnedAt[assessment.normalizedNumber] ?: 0L
                if (now - last < RATE_LIMIT_MS) return@execute
                lastWarnedAt[assessment.normalizedNumber] = now

                postWarning(appContext, assessment)
            } catch (e: Throwable) {
                Log.w(TAG, "Pre-call assessment failed: ${e.message}")
            }
        }
    }

    internal fun postWarning(context: Context, assessment: RiskAssessment) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            ensureChannel(nm)

            val masked = ScamAlertManager.maskNumberForDisplay(assessment.normalizedNumber)
            val title = if (assessment.risk == RiskLevel.HIGH) {
                "Likely Scam Caller: $masked"
            } else {
                "Caution: Suspicious Caller $masked"
            }
            val text = buildString {
                append(assessment.reason)
                if (assessment.risk == RiskLevel.HIGH) append(" Do NOT share OTP or transfer money.")
            }

            nm.notify(assessment.normalizedNumber.hashCode().let { if (it < 0) -it else it },
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setAutoCancel(true)
                    .build()
            )
        } catch (e: Exception) {
            Log.w(TAG, "Pre-call warning notification failed", e)
        }
    }

    fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Pre-Call Warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Heads-up warnings for known scam callers while the phone rings"
                    }
                )
            }
        }
    }
}
