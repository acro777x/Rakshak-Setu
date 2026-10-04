package com.rakshaksetu.app.security

import java.util.Locale

/**
 * Classifies a decoded QR payload and decides what the user should be warned
 * about BEFORE they act on it.
 *
 * WHY THIS EXISTS
 * ---------------
 * The previous QR screen did not scan anything. It hardcoded
 * `var decodedUrl by remember { mutableStateOf("https://sancharsaathi.gov.in") }`
 * and the "Decode QR Code" button simply flipped a phase variable, so the
 * result screen always reported "Verified Safe" for the same government URL
 * regardless of what was actually in front of the camera.
 *
 * WHY QR CODES ARE A PRIMARY VECTOR HERE
 * --------------------------------------
 * A QR code carries no reputation of its own -- it is just a container. The
 * attack is that the user cannot see the destination before acting, and a
 * physical QR sticker can be replaced at any time. The two dangerous families
 * in India are:
 *
 *  1. **UPI collect requests** -- `upi://pay?pa=<handle>...`. Opening one
 *     pre-fills a payment to an attacker-controlled VPA. The single most
 *     important field is `pa`, so it is extracted and displayed.
 *  2. **Payment-app deep links** -- e.g. PhonePe/Google Pay schemes that can
 *     launch a collect or a KYC form directly.
 *
 * Everything else is routed through the URL engine, because a QR that decodes
 * to a link is exactly a link check with an extra step of obfuscation.
 */
object QrPayloadAnalyzer {

    enum class Kind {
        UPI_COLLECT,
        PAYMENT_DEEP_LINK,
        URL,
        WIFI_CREDENTIALS,
        PLAIN_TEXT,
        UNKNOWN
    }

    data class Analysis(
        val raw: String,
        val kind: Kind,
        val label: String,
        /** Populated for UPI collect requests: the payee VPA. */
        val upiHandle: String? = null,
        val payeeName: String? = null,
        val amount: String? = null,
        val note: String? = null,
        val nestedUrl: String? = null,
        val warnings: List<String> = emptyList()
    ) {
        val isActionable: Boolean
            get() = kind == Kind.UPI_COLLECT || kind == Kind.PAYMENT_DEEP_LINK
    }

    private fun param(raw: String, key: String): String? {
        val pattern = Regex("(?i)(^|[?&;])${Regex.escape(key)}=([^&;]*)")
        val m = pattern.find(raw) ?: return null
        return runCatching { java.net.URLDecoder.decode(m.groupValues[2], "UTF-8") }
            .getOrDefault(m.groupValues[2])
            .takeIf { it.isNotBlank() }
    }

    fun analyze(raw: String): Analysis {
        val s = raw.trim()
        if (s.isEmpty()) {
            return Analysis(s, Kind.UNKNOWN, "Empty payload")
        }
        val lower = s.lowercase(Locale.ROOT)

        // ── UPI ────────────────────────────────────────────────────────────
        if (lower.startsWith("upi://")) {
            val handle = param(s, "pa")
            val name = param(s, "pn")
            val amount = param(s, "am")
            val note = param(s, "tn") ?: param(s, "note")

            val warnings = mutableListOf<String>()
            warnings += "This QR pre-fills a UPI payment. The payee handle is shown below - verify it verbally before sending."
            if (amount != null) {
                warnings += "A fixed amount (Rs $amount) is embedded in this code."
            } else {
                warnings += "No amount is embedded; you would choose the amount. Only continue if you initiated this transaction."
            }
            if (handle != null && (handle.contains("fake") || handle.contains("test") || handle.endsWith("@okaxis"))) {
                warnings += "Payee handle '$handle' looks like a placeholder/test handle."
            }
            if (handle != null && handle.matches(Regex("^\\+?\\d{10,14}$"))) {
                warnings += "Payee 'handle' is a plain phone number, not a bank UPI ID. Treat with caution."
            }
            return Analysis(
                raw = s, kind = Kind.UPI_COLLECT,
                label = "UPI payment request",
                upiHandle = handle, payeeName = name, amount = amount, note = note,
                warnings = warnings
            )
        }

        // ── Payment app deep links ──────────────────────────────────────────
        val knownApps = listOf(
            "phonepe", "gpay", "googlepay", "paytm", "bhim", "cred", "amazonpay",
            "freecharge", "mobikwik", "axisbank", "okhdfcbank", "kotak", "ybl"
        )
        if (knownApps.any { lower.contains(it) } && lower.contains("://")) {
            return Analysis(
                raw = s, kind = Kind.PAYMENT_DEEP_LINK,
                label = "Payment app deep link",
                warnings = listOf(
                    "This QR opens a payment app directly, skipping your browser where you could see the address.",
                    "Check the payee name and handle on screen before authorising."
                )
            )
        }

        // ── Wi-Fi credential share ──────────────────────────────────────────
        if (upperOrPrefix(s) == "WIFI" || lower.startsWith("wifi:")) {
            return Analysis(
                raw = s, kind = Kind.WIFI_CREDENTIALS,
                label = "Wi-Fi credential",
                warnings = listOf("Connecting to an unknown hotspot can expose your traffic. Verify the network name with the network owner.")
            )
        }

        // ── URL ────────────────────────────────────────────────────────────
        val extracted = extractUrl(s)
        if (extracted != null) {
            val warnings = mutableListOf<String>()
            if (lower.startsWith("http://")) warnings += "Destination uses unencrypted HTTP."
            return Analysis(
                raw = s, kind = Kind.URL, label = "Web link",
                nestedUrl = extracted, warnings = warnings
            )
        }

        if (lower.startsWith("tel:") || lower.startsWith("smsto:") || lower.startsWith("mailto:")) {
            return Analysis(raw = s, kind = Kind.PLAIN_TEXT, label = "Contact / message action")
        }

        return Analysis(
            raw = s, kind = Kind.PLAIN_TEXT, label = "Plain text",
            note = "This QR carries text, not a link or payment request."
        )
    }

    private fun upperOrPrefix(s: String): String =
        s.substringBefore(':').substringBefore(';').uppercase(Locale.ROOT)

    /** Pulls the first http(s) URL out of a payload that may embed it in text. */
    fun extractUrl(s: String): String? {
        val m = Regex("https?://[^\\s\"'<>]+", RegexOption.IGNORE_CASE).find(s) ?: return null
        return m.value.trimEnd('.', ',', ')')
    }
}
