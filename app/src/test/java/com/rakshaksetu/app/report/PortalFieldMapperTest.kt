package com.rakshaksetu.app.report

import com.rakshaksetu.app.ui.buildFillerScript
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedFieldPolicyTest {

    @Test
    fun `captcha fields are always blocked`() {
        assertTrue(ProtectedFieldPolicy.isProtectedField("txtCaptcha", null, null, "text", null))
        assertTrue(ProtectedFieldPolicy.isProtectedField(null, "captcha_code", null, "text", "Enter CAPTCHA"))
    }

    @Test
    fun `otp and verification codes are blocked`() {
        assertTrue(ProtectedFieldPolicy.isProtectedField("otpInput", null, null, "number", null))
        assertTrue(ProtectedFieldPolicy.isProtectedField(null, null, "enter OTP", "text", null))
        assertTrue(ProtectedFieldPolicy.isProtectedField(null, "verification_code", null, null, null))
    }

    @Test
    fun `passwords blocked by type and name`() {
        assertTrue(ProtectedFieldPolicy.isProtectedField(null, null, null, "password", null))
        assertTrue(ProtectedFieldPolicy.isProtectedField("user_password", null, null, "text", null))
    }

    @Test
    fun `financial identifiers are blocked`() {
        assertTrue(ProtectedFieldPolicy.isProtectedField("aadhaar_no", null, null, null, null))
        assertTrue(ProtectedFieldPolicy.isProtectedField(null, "card_number", null, null, null))
        assertTrue(ProtectedFieldPolicy.isProtectedField("upi_id", null, null, null, null))
    }

    @Test
    fun `ordinary complainant fields pass through`() {
        assertFalse(ProtectedFieldPolicy.isProtectedField("complainant_name", null, null, "text", "Full name"))
        assertFalse(ProtectedFieldPolicy.isProtectedField(null, "mobile_number", "Mobile", "tel", null))
        assertFalse(ProtectedFieldPolicy.isProtectedField("email_id", null, null, "email", null))
        assertFalse(ProtectedFieldPolicy.isProtectedField(null, null, "Describe incident", "textarea", null))
    }
}

class PortalFieldMapperTest {

    @Test
    fun `ncrp payload contains dossier values`() {
        val values = mapOf(
            ReportField.COMPLAINANT_NAME to "Ashish Kumar",
            ReportField.MOBILE to "+919876543210",
            ReportField.INCIDENT_DATE to "23/08/2026",
            ReportField.INCIDENT_DESCRIPTION to "Fraud call received, digital arrest script used."
        )
        val json = PortalFieldMapper.buildPayloadJson(PortalFieldMapper.Portal.NCRP_CYBERCRIME, values)

        assertTrue(json.contains("\"MOBILE\""))
        assertTrue(json.contains("+919876543210"))
        assertTrue(json.contains("digital arrest"))
    }

    @Test
    fun `payload escapes quotes and newlines`() {
        val values = mapOf(
            ReportField.INCIDENT_DESCRIPTION to "Caller said \"transfer now\"\nsecond line"
        )
        val json = PortalFieldMapper.buildPayloadJson(PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI, values)
        assertFalse(json.contains("\nsecond"))
        assertTrue(json.contains("\\n"))
        assertTrue(json.contains("\\\"transfer now\\\""))
    }

    @Test
    fun `chakshu flow excludes address and amount fields`() {
        val fields = PortalFieldMapper.fieldsFor(PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI)
        assertFalse(fields.any { it.field == ReportField.ADDRESS })
        assertFalse(fields.any { it.field == ReportField.FRAUD_AMOUNT })
    }

    @Test
    fun `filler script blocks protected inputs at JS layer too`() {
        val js = buildFillerScript("""[{"field":"MOBILE","value":"+919876543210","kind":"TEL","selectors":["input[name*='mobile' i]"],"labels":["mobile"]}]""")
        assertTrue(js.contains("isProtected"))
        assertTrue(js.contains("'captcha'"))
        assertTrue(js.contains("label"))
        assertTrue(js.contains("AndroidBridge.onFillResults"))
    }
}
