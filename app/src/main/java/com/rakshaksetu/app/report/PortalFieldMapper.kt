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

object PortalFieldMapper {

    enum class Portal(val url: String, val displayName: String, val shortName: String) {
        NCRP_CYBERCRIME(
            "https://cybercrime.gov.in/Webform/Crime_AuthoLogin.aspx?rnt=5",
            "NCRP (cybercrime.gov.in)",
            "NCRP Cybercrime"
        ),
        CHAKSHU_SANCHARSAATHI(
            "https://sancharsaathi.gov.in/sfc/Home/sfc-complaint.jsp",
            "Chakshu (Sanchar Saathi)",
            "Chakshu Telecom"
        )
    }

    data class FieldSpec(
        val field: ReportField,
        val selectorCandidates: List<String>,
        val inputType: InputKind,
        val labelKeywords: List<String> = emptyList()
    )

    enum class InputKind { TEXT, EMAIL, TEL, NUMBER, DATE_TEXT, TIME_TEXT, TEXTAREA }

    private val NAME_SELECTORS = listOf(
        "#complainant_name", "input[name*='name' i]:not([name*='firm' i]):not([name*='user' i])",
        "input[placeholder*='name' i]", "input[id*='txtName' i]"
    )
    private val MOBILE_SELECTORS = listOf(
        "#mobile", "input[name*='mobile' i]", "input[name*='phone' i]",
        "input[type='tel']", "input[id*='txtMobile' i]", "input[placeholder*='mobile' i]"
    )
    private val EMAIL_SELECTORS = listOf(
        "#email", "input[name*='email' i]", "input[type='email']", "input[id*='txtEmail' i]", "input[placeholder*='email' i]"
    )
    private val STATE_SELECTORS = listOf(
        "#state", "select[name*='state' i]", "input[name*='state' i]", "select[id*='ddlState' i]", "select[id*='State' i]"
    )
    private val CITY_SELECTORS = listOf(
        "#district", "#city", "select[name*='district' i]", "select[name*='city' i]", "input[name*='city' i]"
    )
    private val ADDRESS_SELECTORS = listOf(
        "#address", "textarea[name*='address' i]", "input[name*='address' i]", "textarea[id*='txtAddress' i]"
    )
    private val DESC_SELECTORS = listOf(
        "#description", "#incident_details", "#complaint_description",
        "textarea[name*='description' i]", "textarea[name*='incident' i]",
        "textarea[name*='complaint' i]", "textarea[id*='txtRemarks' i]"
    )
    private val AMOUNT_SELECTORS = listOf(
        "#amount", "input[name*='amount' i]", "input[name*='loss' i]"
    )
    private val DATE_SELECTORS = listOf(
        "#incident_date", "input[name*='date' i]", "input[id*='txtDate' i]"
    )
    private val TIME_SELECTORS = listOf(
        "#incident_time", "input[name*='time' i]", "input[id*='txtTime' i]"
    )
    private val SUSPECT_SELECTORS = listOf(
        "#suspect_mobile", "#suspect_phone", "input[name*='suspect' i]",
        "input[placeholder*='suspect' i]", "input[id*='txtSuspect' i]"
    )

    val NCRP_FIELDS: List<FieldSpec> = listOf(
        FieldSpec(ReportField.COMPLAINANT_NAME, NAME_SELECTORS, InputKind.TEXT),
        FieldSpec(ReportField.MOBILE, MOBILE_SELECTORS, InputKind.TEL),
        FieldSpec(ReportField.EMAIL, EMAIL_SELECTORS, InputKind.EMAIL),
        FieldSpec(ReportField.STATE, STATE_SELECTORS, InputKind.TEXT),
        FieldSpec(ReportField.CITY, CITY_SELECTORS, InputKind.TEXT),
        FieldSpec(ReportField.ADDRESS, ADDRESS_SELECTORS, InputKind.TEXTAREA),
        FieldSpec(ReportField.INCIDENT_DESCRIPTION, DESC_SELECTORS, InputKind.TEXTAREA),
        FieldSpec(ReportField.FRAUD_AMOUNT, AMOUNT_SELECTORS, InputKind.NUMBER),
        FieldSpec(ReportField.INCIDENT_DATE, DATE_SELECTORS, InputKind.DATE_TEXT),
        FieldSpec(ReportField.INCIDENT_TIME, TIME_SELECTORS, InputKind.TIME_TEXT),
        FieldSpec(ReportField.SUSPECT_PHONE, SUSPECT_SELECTORS, InputKind.TEL)
    )

    val CHAKSHU_FIELDS: List<FieldSpec> = listOf(
        FieldSpec(ReportField.SUSPECT_PHONE, SUSPECT_SELECTORS, InputKind.TEL),
        FieldSpec(ReportField.INCIDENT_DATE, DATE_SELECTORS, InputKind.DATE_TEXT),
        FieldSpec(ReportField.INCIDENT_TIME, TIME_SELECTORS, InputKind.TIME_TEXT),
        FieldSpec(ReportField.INCIDENT_DESCRIPTION, DESC_SELECTORS, InputKind.TEXTAREA)
    )

    fun fieldsFor(portal: Portal): List<FieldSpec> =
        if (portal == Portal.NCRP_CYBERCRIME) NCRP_FIELDS else CHAKSHU_FIELDS

    fun buildPayloadJson(portal: Portal, values: Map<ReportField, String>): String {
        val entries = values.map { (k, v) ->
            val escaped = v.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
            "\"" + k.name + "\":\"" + escaped + "\""
        }
        return "{" + entries.joinToString(",") + "}"
    }
}
