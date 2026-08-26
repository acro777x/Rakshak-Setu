package com.rakshaksetu.app.community

/**
 * Pure phone-number normalization for blacklist lookups.
 * Strategy: digits-only, then collapse Indian numbers to the 10-digit national
 * significant number so +91 / 91 / 0 / bare formats all collide on one key.
 * Non-Indian numbers normalize to their full digit string with country prefix.
 */
object NumberNormalizer {

    private val NON_DIGIT = Regex("\\D")

    fun normalize(raw: String): String {
        val digits = NON_DIGIT.replace(raw, "")
        if (digits.isEmpty()) return ""

        // Emergency / short-code services are never normalized down
        if (digits.length <= 5) return digits

        // India country code collapse
        if (digits.length == 12 && digits.startsWith("91")) return digits.substring(2)
        if (digits.length == 13 && digits.startsWith("091")) return digits.substring(3)
        if (digits.length == 11 && digits.startsWith("0")) return digits.substring(1)

        return digits
    }

    fun isIndianNational(normalized: String): Boolean = normalized.length == 10

    fun isInternational(normalized: String): Boolean = normalized.length > 10
}
