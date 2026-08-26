package com.rakshaksetu.app.elder

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import com.rakshaksetu.app.receiver.ElderAlertReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Pluggable emergency SMS delivery (M3 hardening).
 *
 * Android does NOT block third-party apps from SENDING SMS via SmsManager —
 * but carrier filtering, OEM "silent SMS" throttling, and Play-policy
 * restrictions on the SEND_SMS permission can make direct delivery unreliable.
 * [HttpGatewayTransport] provides the user-configured escape hatch: any SMS
 * gateway with a simple HTTP API (self-hosted SMS-gateway apps, MSG91/Fast2SMS
 * style providers, Twilio-compatible relays) can be wired in by the user.
 *
 * Dispatch order in [EmergencyDispatcher]: direct device SMS first (works
 * offline — critical for emergencies), then the HTTP gateway for any guardian
 * whose direct send could not be queued.
 */
interface SmsTransport {
    /** @return true when the message was accepted for delivery. */
    suspend fun send(to: String, body: String): Boolean
}

class DirectSmsTransport(private val context: Context) : SmsTransport {

    @SuppressLint("MissingPermission")
    override suspend fun send(to: String, body: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return@withContext false

            val sentIntent = Intent(context, ElderAlertReceiver::class.java).apply {
                action = ElderAlertReceiver.ACTION_SMS_SENT
                putExtra(ElderAlertReceiver.EXTRA_GUARDIAN_NUMBER, to)
            }
            val sentPending = PendingIntent.getBroadcast(
                context,
                ("direct$to$body").hashCode(),
                sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val parts = smsManager.divideMessage(body)
            if (parts.size == 1) {
                smsManager.sendTextMessage(to, null, body, sentPending, null)
            } else {
                smsManager.sendMultipartTextMessage(to, null, parts, arrayListOf(sentPending), null)
            }
            Log.i("SmsTransport", "Direct SMS queued to ${to.takeLast(4).padStart(10, 'X')}")
            true
        } catch (e: Exception) {
            Log.e("SmsTransport", "Direct SMS failed for ${to.takeLast(4)}: ${e.message}")
            false
        }
    }
}

/**
 * Posts the message to a user-supplied HTTP SMS gateway.
 *
 * Supported endpoint styles (configured by the user in Elder Mode settings):
 *  1. Plain JSON API: POST {"to": "...", "body": "..."} to the raw URL.
 *  2. Template URL: placeholders {to} and {body} are substituted and GET-sent
 *     (matches most free SMS API providers' simple GET format).
 */
class HttpGatewayTransport(private val endpoint: String) : SmsTransport {

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    override suspend fun send(to: String, body: String): Boolean = withContext(Dispatchers.IO) {
        if (endpoint.isBlank()) return@withContext false
        try {
            val request = if (endpoint.contains("{to}") || endpoint.contains("{body}")) {
                val url = endpoint
                    .replace("{to}", java.net.URLEncoder.encode(to, "UTF-8"))
                    .replace("{body}", java.net.URLEncoder.encode(body, "UTF-8"))
                Request.Builder().url(url).get().build()
            } else {
                val payload = "{\"to\":${jsonQuote(to)},\"body\":${jsonQuote(body)}}"
                Request.Builder().url(endpoint).post(payload.toRequestBody(JSON)).build()
            }

            client.newCall(request).execute().use { response ->
                val ok = response.isSuccessful
                Log.i("SmsTransport", "Gateway response ${response.code} for ${to.takeLast(4)}")
                ok
            }
        } catch (e: Exception) {
            Log.e("SmsTransport", "Gateway send failed: ${e.message}")
            false
        }
    }

    private fun jsonQuote(raw: String): String =
        "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\""
}
