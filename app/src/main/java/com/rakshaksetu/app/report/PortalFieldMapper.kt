package com.rakshaksetu.app.report

/**
 * Logical fields the reporting assistant can fill on government portals.
 * Values are resolved from [UserProfileStore] + the incident dossier.
 */
enum class ReportField(val label: String) {
    COMPLAINANT_NAME("Complainant Name"),
    MOBILE("Mobile Number"),
    ALT_MOBILE("Alternate Mobile"),
    EMAIL("Email"),
    STATE("State / UT"),
    CITY("City / District"),
    ADDRESS("Address"),
    AGE("Age"),
    INCIDENT_DESCRIPTION("Incident Description"),
    FRAUD_AMOUNT("Fraud Amount"),
    INCIDENT_DATE("Incident Date"),
    INCIDENT_TIME("Incident Time"),
    SUSPECT_PHONE("Suspect Phone Number")
}

/**
 * SECURITY BOUND (hard requirement):
 * CAPTCHA, OTP, password, PIN, Aadhaar-number and payment inputs are NEVER
 * auto-filled or auto-submitted. [isProtectedField] fails CLOSED (aggressive
 * substring match) and is enforced identically on the Kotlin side and inside
 * the injected JavaScript.
 */
object ProtectedFieldPolicy {

    private val BLOCKLIST_TOKENS = listOf(
        "captcha", "capcha", "captch",
        "otp", "one_time_password", "onetimepassword", "verification_code", "verifycode",
        "password", "passwd", "pwd",
        "pin", "passcode",
        "aadhaar", "aadhar", "uidai", "vid_number",
        "cvv", "card_number", "cardnumber", "account_number", "accountnumber", "acct",
        "upi", "ifsc", "debit", "credit_card"
    )

    /** True when an element's identifying attributes mark it as protected. */
    fun isProtectedField(id: String?, name: String?, placeholder: String?, type: String?, ariaLabel: String?): Boolean {
        val haystack = listOf(id, name, placeholder, ariaLabel)
            .filterNotNull()
            .joinToString(" ")
            .lowercase()

        if (type.equals("password", ignoreCase = true)) return true
        if (type.equals("file", ignoreCase = true)) return true

        return BLOCKLIST_TOKENS.any { haystack.contains(it) }
    }
}

/**
 * Portal registry + DOM matching strategy for the two supported complaint flows.
 *
 * Matching happens in TWO passes inside the injected script:
 *  1. [FieldSpec.selectorCandidates] — direct CSS selectors (fast path).
 *  2. [FieldSpec.labelKeywords] — associated-label text matching (robust against
 *     ASP.NET auto-generated ids like ctl00_ContentPlaceHolder1_txtName, which
 *     dominate government portals). Includes Hinglish label variants.
 */
object PortalFieldMapper {

    enum class Portal(val url: String, val displayName: String) {
        NCRP_CYBERCRIME("https://cybercrime.gov.in/Webpages/Citizen_Login.aspx", "NCRP (cybercrime.gov.in)"),
        CHAKSHU_SANCHARSAATHI("https://sancharsaathi.gov.in/sfc/", "Chakshu (Sanchar Saathi)")
    }

    data class FieldSpec(
        val field: ReportField,
        val selectorCandidates: List<String>,
        val inputType: InputKind,
        val labelKeywords: List<String> = emptyList()
    )

    enum class InputKind { TEXT, EMAIL, TEL, NUMBER, DATE_TEXT, TIME_TEXT, TEXTAREA }

    private val NAME_LABELS = listOf("name", "naam", "your name")
    private val MOBILE_LABELS = listOf("mobile", "phone", "contact number", "mobile number")
    private val EMAIL_LABELS = listOf("email", "e-mail", "mail id")
    private val STATE_LABELS = listOf("state", "rajya")
    private val CITY_LABELS = listOf("district", "city", "zilla")
    private val ADDRESS_LABELS = listOf("address", "pata")
    private val DESC_LABELS = listOf(
        "description", "complaint", "incident", "details of complaint",
        "what happened", "brief facts", "describe"
    )
    private val AMOUNT_LABELS = listOf("amount", "loss")
    private val DATE_LABELS = listOf("date of incident", "incident date", "date")
    private val TIME_LABELS = listOf("time of incident", "incident time", "time")
    private val SUSPECT_LABELS = listOf(
        "suspect", "accused", "fraudster", "scammer", "suspect mobile",
        "mobile number of suspect", "phone number of accused", "caller number"
    )

    private val NAME_SELECTORS = listOf(
        "#complainant_name", "input[name*='name' i]:not([name*='firm' i]):not([name*='user' i])",
        "input[placeholder*='name' i]", "input[id*='txtName' i]"
    )
    private val MOBILE_SELECTORS = listOf(
        "#mobile_number", "input[type='tel']", "input[name*='mobile' i]",
        "input[name*='phone' i]", "input[placeholder*='mobile' i]", "input[id*='txtMobile' i]"
    )
    private val EMAIL_SELECTORS = listOf(
        "#email_id", "input[type='email']", "input[name*='email' i]", "input[placeholder*='email' i]"
    )
    private val STATE_SELECTORS = listOf(
        "select#state", "select[name*='state' i]", "select[id*='ddlState' i]"
    )
    private val CITY_SELECTORS = listOf(
        "select#district", "select[name*='district' i]", "select[id*='ddlDistrict' i]",
        "input[name*='city' i]", "input[placeholder*='city' i]"
    )
    private val ADDRESS_SELECTORS = listOf(
        "textarea[name*='address' i]", "textarea[placeholder*='address' i]", "#address"
    )
    private val DESCRIPTION_SELECTORS = listOf(
        "textarea#complaint_description", "textarea[name*='complaint' i]",
        "textarea[name*='description' i]", "textarea[placeholder*='incident' i]",
        "textarea[placeholder*='describe' i]", "textarea[id*='txtComplaint' i]",
        "textarea[id*='txtDesc' i]"
    )
    private val AMOUNT_SELECTORS = listOf(
        "input[name*='amount' i]", "input[placeholder*='amount' i]", "input[id*='txtAmt' i]"
    )
    private val DATE_SELECTORS = listOf(
        "input[type='date']", "input[name*='date' i]:not([name*='update' i])"
    )
    private val TIME_SELECTORS = listOf(
        "input[type='time']", "input[name*='time' i]"
    )
    private val SUSPECT_SELECTORS = listOf(
        "input[name*='suspect_mobile' i]", "input[name*='fraud_mobile' i]",
        "input[name*='accused' i]", "input[placeholder*='suspect' i]",
        "input[id*='txtSuspect' i]"
    )

    val NCRP_FIELDS: List<FieldSpec> = listOf(
        FieldSpec(ReportField.COMPLAINANT_NAME, NAME_SELECTORS, InputKind.TEXT, NAME_LABELS),
        FieldSpec(ReportField.MOBILE, MOBILE_SELECTORS, InputKind.TEL, MOBILE_LABELS),
        FieldSpec(ReportField.EMAIL, EMAIL_SELECTORS, InputKind.EMAIL, EMAIL_LABELS),
        FieldSpec(ReportField.STATE, STATE_SELECTORS, InputKind.TEXT, STATE_LABELS),
        FieldSpec(ReportField.CITY, CITY_SELECTORS, InputKind.TEXT, CITY_LABELS),
        FieldSpec(ReportField.ADDRESS, ADDRESS_SELECTORS, InputKind.TEXTAREA, ADDRESS_LABELS),
        FieldSpec(ReportField.INCIDENT_DESCRIPTION, DESCRIPTION_SELECTORS, InputKind.TEXTAREA, DESC_LABELS),
        FieldSpec(ReportField.FRAUD_AMOUNT, AMOUNT_SELECTORS, InputKind.NUMBER, AMOUNT_LABELS),
        FieldSpec(ReportField.INCIDENT_DATE, DATE_SELECTORS, InputKind.DATE_TEXT, DATE_LABELS),
        FieldSpec(ReportField.INCIDENT_TIME, TIME_SELECTORS, InputKind.TIME_TEXT, TIME_LABELS),
        FieldSpec(ReportField.SUSPECT_PHONE, SUSPECT_SELECTORS, InputKind.TEL, SUSPECT_LABELS)
    )

    val CHAKSHU_FIELDS: List<FieldSpec> = listOf(
        FieldSpec(ReportField.COMPLAINANT_NAME, NAME_SELECTORS, InputKind.TEXT, NAME_LABELS),
        FieldSpec(ReportField.MOBILE, MOBILE_SELECTORS, InputKind.TEL, MOBILE_LABELS),
        FieldSpec(ReportField.SUSPECT_PHONE, SUSPECT_SELECTORS, InputKind.TEL, SUSPECT_LABELS),
        FieldSpec(ReportField.INCIDENT_DESCRIPTION, DESCRIPTION_SELECTORS, InputKind.TEXTAREA, DESC_LABELS),
        FieldSpec(ReportField.INCIDENT_DATE, DATE_SELECTORS, InputKind.DATE_TEXT, DATE_LABELS),
        FieldSpec(ReportField.INCIDENT_TIME, TIME_SELECTORS, InputKind.TIME_TEXT, TIME_LABELS)
    )

    fun fieldsFor(portal: Portal): List<FieldSpec> = when (portal) {
        Portal.NCRP_CYBERCRIME -> NCRP_FIELDS
        Portal.CHAKSHU_SANCHARSAATHI -> CHAKSHU_FIELDS
    }

    /**
     * Builds the JSON payload consumed by the WebView injector:
     * [{field, value, kind, selectors[], labels[]}, ...]
     */
    fun buildPayloadJson(portal: Portal, values: Map<ReportField, String>): String {
        val arr = fieldsFor(portal).mapNotNull { spec ->
            val value = values[spec.field]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            "{\"field\":\"${spec.field.name}\"," +
                "\"value\":${jsonEscape(value)}," +
                "\"kind\":\"${spec.inputType.name}\"," +
                "\"selectors\":[${spec.selectorCandidates.joinToString(",") { jsonEscape(it) }}]," +
                "\"labels\":[${spec.labelKeywords.joinToString(",") { jsonEscape(it) }}]}"
        }
        return "[${arr.joinToString(",")}]"
    }

    private fun jsonEscape(raw: String): String {
        val sb = StringBuilder("\"")
        raw.forEach { ch ->
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch.code < 0x20) sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
            }
        }
        return sb.append('"').toString()
    }
}
