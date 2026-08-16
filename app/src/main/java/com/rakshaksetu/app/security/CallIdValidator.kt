package com.rakshaksetu.app.security

/**
 * Validates callId to prevent path traversal attacks.
 * AI pipeline is UNTRUSTED input — attacker controls call audio
 * which controls DetectionResult content including callId.
 */
object CallIdValidator {
    private val UUID_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    fun isValid(callId: String): Boolean {
        return callId.matches(UUID_REGEX)
    }

    fun requireValid(callId: String): String {
        require(isValid(callId)) { "Invalid callId format: $callId — expected UUID" }
        return callId
    }
}
