package com.rakshaksetu.app.elder

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Elder Mode configuration store (DPDP: all data stays on-device).
 * Guardians are trusted contacts receiving emergency scam-call SMS alerts.
 */
class ElderModeStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("rakshak_elder_mode", Context.MODE_PRIVATE)

    companion object {
        const val MAX_GUARDIANS = 3
        private const val KEY_ENABLED = "key_enabled"
        private const val KEY_AUTO_SEND = "key_auto_send_sms"
        private const val KEY_GUARDIANS_JSON = "key_guardians_json"
        private const val KEY_GATEWAY_URL = "key_gateway_url"

        /** Auto-send fires only at/above this confidence; one-tap works from 60%. */
        const val AUTO_SEND_CONFIDENCE_THRESHOLD = 0.85f

        /** Minimum gap between two SMS bursts to the SAME guardian. */
        const val PER_GUARDIAN_RATE_LIMIT_MS = 6 * 60 * 60 * 1000L
    }

    data class Guardian(
        val name: String,
        val number: String
    )

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var autoSendSmsEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SEND, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SEND, value).apply()

    /**
     * Optional HTTP SMS gateway endpoint (user-provided; never bundled).
     * Raw JSON POST URL, or a template with {to}/{body} placeholders for
     * GET-style free SMS APIs.
     */
    var smsGatewayUrl: String
        get() = prefs.getString(KEY_GATEWAY_URL, "").orEmpty().trim()
        set(value) = prefs.edit().putString(KEY_GATEWAY_URL, value.trim()).apply()

    fun getGuardians(): List<Guardian> =
        try {
            val json = prefs.getString(KEY_GUARDIANS_JSON, null)
            if (json.isNullOrBlank()) emptyList()
            else {
                val type = object : TypeToken<List<Guardian>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }

    /**
     * Replaces the guardian list. Numbers are validated (10-13 digits after
     * normalization); invalid entries are rejected wholesale.
     */
    fun setGuardians(guardians: List<Guardian>): Boolean {
        if (guardians.size > MAX_GUARDIANS) return false
        if (guardians.any { it.name.isBlank() || !isValidNumber(it.number) }) return false
        return try {
            prefs.edit().putString(KEY_GUARDIANS_JSON, Gson().toJson(guardians)).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun addGuardian(guardian: Guardian): Boolean {
        val current = getGuardians().toMutableList()
        if (current.any { normalizeNumber(it.number) == normalizeNumber(guardian.number) }) return false
        current.add(guardian)
        return setGuardians(current)
    }

    fun removeGuardian(number: String): Boolean {
        val target = normalizeNumber(number)
        val next = getGuardians().filterNot { normalizeNumber(it.number) == target }
        return setGuardians(next)
    }

    fun lastSmsEpochMs(guardianNumber: String): Long =
        prefs.getLong(rateKey(guardianNumber), 0L)

    fun markSmsSent(guardianNumber: String, epochMs: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(rateKey(guardianNumber), epochMs).apply()
    }

    private fun rateKey(number: String) = "key_last_sms_${normalizeNumber(number)}"

    internal fun isValidNumber(raw: String): Boolean {
        val digits = raw.filter { it.isDigit() }
        return digits.length in 10..13
    }

    internal fun normalizeNumber(raw: String): String = raw.filter { it.isDigit() }.takeLast(10)
}
