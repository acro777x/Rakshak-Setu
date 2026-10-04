package com.rakshaksetu.app.security

import java.util.Locale
import java.util.regex.Pattern

/**
 * Deterministic, on-device URL reputation engine.
 *
 * WHY THIS EXISTS
 * ---------------
 * The previous implementation in ScanScreens.checkUrl() was a substring test:
 * `lower.contains("kyc") || lower.contains("otp") || lower.contains("apk")`.
 * That flags google.com/kyc-account-help and is defeated by
 * `g00gle-kyc.tk`; it never touched the network, so it could not know whether a
 * host was on a real phishing feed. It printed "SSL Certificate: Valid TLS 1.3"
 * as hardcoded text without ever opening a socket.
 *
 * WHAT THIS DOES INSTEAD
 * ----------------------
 * A layered decision over actually-observed facts:
 *
 *   Layer 1  Structured parse      -- scheme/host/userinfo/port, RFC-correct
 *   Layer 2  Brand-impersonation    -- "sbi-kyc-update.in" -> SBI
 *   Layer 3  DGA / homograph        -- punycode xn--, digit-letter swaps
 *   Layer 4  Host heuristics        -- raw IP host, deep subdomain, risky TLD
 *   Layer 5  Live feeds             -- merged in by [UrlThreatScanner]
 *
 * LAYER 5 IS THE AUTHORITATIVE ONE. Layers 1-4 are a fast offline prior that
 * also works in airplane mode; they must never be presented as a verdict from
 * a threat-intelligence feed. [Verdict] keeps the two apart on purpose.
 *
 * Every rule is a NAMED constant so a finding can be explained to the user in
 * plain language and unit-tested in isolation.
 */

enum class UrlRisk { SAFE, SUSPICIOUS, DANGEROUS }

/** Where a verdict came from, so the UI never implies a verdict we did not earn. */
enum class EvidenceSource {
    /** No live feed reachable; only the offline prior ran. */
    OFFLINE_HEURISTIC,
    /** At least one live reputation feed returned a match. */
    LIVE_FEED,
    /** Live feeds were consulted and did not list the host. */
    LIVE_FEED_CLEAN
}

data class UrlFinding(val rule: String, val detail: String, val weight: Int)

data class Verdict(
    val input: String,
    val normalized: String,
    val host: String,
    val registrableDomain: String,
    val scheme: String,
    val risk: UrlRisk,
    /** 0..100, higher is worse. Summed rule weights, not cosmetic. */
    val score: Int,
    val findings: List<UrlFinding>,
    val source: EvidenceSource,
    val feedNames: List<String> = emptyList()
)

object UrlThreatHeuristics {

    // â”€â”€ Layer 2: brands targeted by Indian financial phishing â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    // brand token -> (official registrable domains, severity weight).
    // A host that CONTAINS a brand token but is NOT on its official list is
    // impersonation. Highest-signal rule for the Indian threat landscape.
    private val BRANDS: Map<String, Pair<Set<String>, Int>> = mapOf(
        "sbi" to (setOf("sbi.co.in", "sbi.in") to 45),
        "hdfc" to (setOf("hdfcbank.com", "hdfcnetbank.com") to 45),
        "icici" to (setOf("icicibank.com", "icici.com") to 45),
        "axis" to (setOf("axisbank.com", "axisbank.co.in") to 45),
        "kotak" to (setOf("kotak.com", "kotakbank.com") to 45),
        "pnb" to (setOf("pnb.co.in") to 45),
        "boi" to (setOf("bankofindia.com") to 40),
        "paytm" to (setOf("paytm.com") to 40),
        "phonepe" to (setOf("phonepe.com") to 40),
        "gpay" to (setOf("google.com") to 35),
        "whatsapp" to (setOf("whatsapp.com", "wa.me") to 35),
        "kyc" to (setOf("uidai.gov.in", "digitalsseuidai.gov.in") to 40),
        "aadhaar" to (setOf("uidai.gov.in", "digitalsseuidai.gov.in") to 40),
        "upi" to (setOf("npci.org.in") to 40),
        "rbi" to (setOf("rbi.org.in") to 35),
        "incometax" to (setOf("incometax.gov.in", "incometaxindia.gov.in") to 40),
        "police" to (setOf("mha.gov.in") to 40),
        "cyber" to (setOf("cybercrime.gov.in", "mhcyber.gov.in") to 35),
        "ecourts" to (setOf("ecourts.gov.in") to 30),
        "amazon" to (setOf("amazon.in", "amazon.com") to 30),
        "flipkart" to (setOf("flipkart.com") to 30)
    )

    // â”€â”€ Layer 4: keywords seen in real credential-harvesting lures â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    private val LURE_KEYWORDS = listOf(
        "kycupdate", "kycverify", "kycexpired", "otpshare", "otp", "refund",
        "account-suspend", "accountsuspend", "verify-now", "login-now",
        "secure-login", "credential", "password-reset", "update-detail",
        "beneficiary", "award", "lottery", "prize", "giftcard", "free-gift",
        "bonus-credit", "customs", "redelivery", "courier-fee", "tax-refund",
        "netbanking", "atm", "pin-verify", "account-closed", "demat",
        "investment", "doubling", "crypto", "mining"
    )

    // TLDs with persistently high abuse rates. A weight, not a verdict alone.
    private val RISKY_TLDS = mapOf(
        "tk" to 25, "ml" to 25, "ga" to 25, "cf" to 25, "gq" to 25, "zip" to 30,
        "mov" to 30, "top" to 20, "xyz" to 15, "click" to 25, "link" to 20,
        "work" to 20, "rest" to 20, "country" to 20, "cam" to 25, "loan" to 20,
        "download" to 25, "racing" to 20, "stream" to 20, "bid" to 20
    )

    // â”€â”€ Layer 3: confusables (target char -> visually identical chars) â”€â”€â”€â”€â”€â”€â”€
    private val CONFUSABLES = mapOf(
        '0' to "o", 'o' to "0", '1' to "il", 'l' to "1i", '5' to "s", 's' to "5",
        '3' to "e", 'e' to "3", '4' to "a", 'a' to "4", '8' to "b", 'b' to "8",
        'g' to "q", 'q' to "g", 'v' to "u", 'u' to "v", 'm' to "rn", 'n' to "m"
    )

    private val PUBLIC_SUFFIX_2 = setOf(
        "co.in", "net.in", "org.in", "gov.in", "ac.in", "edu.in", "res.in",
        "firm.in", "gen.in", "ind.in", "com.au", "co.uk", "co.jp", "com.br", "co.za"
    )

    private val IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    /** Result of a strict URL parse. [ok] false means we refused to guess. */
    data class Parsed(
        val ok: Boolean,
        val scheme: String = "",
        val host: String = "",
        val port: String = "",
        val path: String = "",
        val hasUserInfo: Boolean = false,
        val error: String? = null
    )


    fun parse(raw: String): Parsed {
        val s = raw.trim()
        if (s.isEmpty()) return Parsed(false, error = "Empty input")

        val schemeMatch = Pattern.compile("^([a-zA-Z][a-zA-Z0-9+.-]*)://").matcher(s)
        if (!schemeMatch.find()) return Parsed(false, error = "Missing scheme (http:// or https://)")
        val scheme = schemeMatch.group(1).lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return Parsed(false, scheme = scheme, error = "Unsupported scheme '$scheme'")
        }

        val rest = s.substring(schemeMatch.end())
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val path = if ('/' in rest) rest.substringAfter('/') else ""

        if (authority.isEmpty()) return Parsed(false, scheme = scheme, error = "No host")

        var hasUserInfo = false
        var hostPart = authority
        val at = authority.lastIndexOf('@')
        if (at >= 0) {
            hasUserInfo = true
            hostPart = authority.substring(at + 1)
        }

        var port = ""
        if (hostPart.startsWith("[")) {                       // IPv6 literal
            val close = hostPart.indexOf(']')
            if (close < 0) return Parsed(false, scheme = scheme, error = "Malformed IPv6 literal")
            val after = hostPart.substring(close + 1)
            if (after.startsWith(":")) port = after.substring(1)
            hostPart = hostPart.substring(0, close + 1)
        } else {
            val colon = hostPart.lastIndexOf(':')
            if (colon >= 0) {
                port = hostPart.substring(colon + 1)
                hostPart = hostPart.substring(0, colon)
            }
        }

        if (hostPart.isEmpty()) return Parsed(false, scheme = scheme, error = "No host")
        return Parsed(true, scheme, hostPart.lowercase(Locale.ROOT), port, path, hasUserInfo)
    }

    /** eTLD+1 approximation: strips multi-label public suffixes like "co.in". */
    fun registrableDomain(host: String): String {
        val labels = host.split(".").filter { it.isNotEmpty() }
        if (labels.size <= 2) return host
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (PUBLIC_SUFFIX_2.contains(lastTwo) && labels.size >= 3) {
            labels.takeLast(3).joinToString(".")
        } else lastTwo
    }

    fun analyze(raw: String): Verdict {
        val parsed = parse(raw)
        if (!parsed.ok) {
            return Verdict(
                input = raw, normalized = raw, host = "", registrableDomain = "",
                scheme = parsed.scheme, risk = UrlRisk.SUSPICIOUS, score = 30,
                findings = listOf(UrlFinding("PARSE", parsed.error ?: "Unparseable URL", 30)),
                source = EvidenceSource.OFFLINE_HEURISTIC
            )
        }

        val host = parsed.host
        val reg = registrableDomain(host)
        val f = mutableListOf<UrlFinding>()

        // Layer 4a: raw IP host.
        val ip = IPV4.matcher(host)
        if (ip.matches()) {
            val octets = (1..4).map { ip.group(it)!!.toInt() }
            val valid = octets.all { it in 0..255 }
            val isPrivate = octets[0] == 10 || octets[0] == 127 ||
                (octets[0] == 172 && octets[1] in 16..31) || octets[0] == 192
            if (valid && !isPrivate) {
                f += UrlFinding("IP_HOST", "Host is a bare IP address ($host), not a domain", 35)
            }
        }

        // Layer 4b: plaintext HTTP.
        if (parsed.scheme == "http") {
            f += UrlFinding("NO_TLS", "Connection is plaintext HTTP - traffic can be intercepted", 20)
        }

        // Layer 4c: embedded credentials (phishing form bait).
        if (parsed.hasUserInfo) {
            f += UrlFinding("USERINFO", "URL embeds credentials before '@' (classic lure)", 30)
        }

        // Layer 3a: punycode / mixed-script.
        if (host.contains("xn--")) {
            f += UrlFinding("PUNYCODE", "Host uses punycode (xn--), often used to imitate other scripts", 30)
        }

        // Layer 4d: subdomain depth.
        val labelCount = host.split(".").count { it.isNotEmpty() }
        if (labelCount >= 5) {
            f += UrlFinding("DEEP_SUBDOMAIN", "$labelCount subdomain levels - brand buried in the path", 15)
        }

        // Layer 4e: hyphen-heavy labels.
        val hostLabel = host.substringBefore(".")
        val hyphens = hostLabel.count { it == '-' }
        if (hyphens >= 3) {
            f += UrlFinding("HYPHEN_DENSE", "Host label contains $hyphens hyphens - keyword stuffing pattern", 20)
        }

        collectBrandFindings(host, f)
        collectDomainFindings(host, reg, parsed, f)

        val score = f.sumOf { it.weight }.coerceIn(0, 100)
        val risk = when {
            score >= 60 -> UrlRisk.DANGEROUS
            score >= 25 -> UrlRisk.SUSPICIOUS
            else -> UrlRisk.SAFE
        }

        val normPort = if (parsed.port.isNotEmpty()) ":${parsed.port}" else ""
        val normPath = if (parsed.path.isNotEmpty()) "/${parsed.path}" else ""

        return Verdict(
            input = raw,
            normalized = "${parsed.scheme}://$host$normPort$normPath",
            host = host,
            registrableDomain = reg,
            scheme = parsed.scheme,
            risk = risk,
            score = score,
            findings = f.sortedByDescending { it.weight },
            source = EvidenceSource.OFFLINE_HEURISTIC
        )
    }

    /** Layer 2: brand impersonation against the official-domain allowlist. */
    private fun collectBrandFindings(host: String, f: MutableList<UrlFinding>) {
        val cleanHost = host.replace("-", "").replace(".", "").replace("_", "")
        BRANDS.forEach { (token, spec) ->
            val (official, weight) = spec
            val mentions = host.contains(token, ignoreCase = true) ||
                cleanHost.contains(token, ignoreCase = true)
            if (!mentions) return@forEach
            val isOfficial = official.any { host == it || host.endsWith(".$it") }
            if (!isOfficial) {
                f += UrlFinding(
                    "BRAND_IMPERSONATION",
                    "Claims '$token' but is NOT an official domain (official: ${official.joinToString(", ")})",
                    weight
                )
            }
        }
    }

    /** Layers 3b and 4f-i: homoglyph, lure keywords, TLD, payload path, port. */
    private fun collectDomainFindings(
        host: String,
        reg: String,
        parsed: Parsed,
        f: MutableList<UrlFinding>
    ) {
        val regLabel = reg.substringBefore(".")
        confusableHits(regLabel)?.let {
            f += UrlFinding("HOMOGLYPH", "Label '$regLabel' uses confusable characters and imitates '$it'", 30)
        }
        val hay = "$host/${parsed.path}".lowercase(Locale.ROOT)
        // Normalize separators so "kyc-update" also matches the "kycupdate" keyword.
        val hayCompact = hay.replace("-", "").replace("_", "").replace(".", "")
        val hits = (LURE_KEYWORDS.filter { hay.contains(it) } +
            LURE_KEYWORDS.filter { hayCompact.contains(it) }).distinct()
        if (hits.isNotEmpty()) {
            f += UrlFinding(
                "LURE_KEYWORD",
                "Credential-harvesting keywords: ${hits.take(4).joinToString(", ")}",
                minOf(40, 8 * hits.size)
            )
        }

        val tld = reg.substringAfterLast(".", "")
        RISKY_TLDS[tld]?.let { w ->
            f += UrlFinding("RISKY_TLD", "Top-level domain '.$tld' has a high abuse rate", w)
        }

        val lowerPath = parsed.path.lowercase(Locale.ROOT)
        if (lowerPath.endsWith(".apk") || lowerPath.endsWith(".exe") || lowerPath.contains("download")) {
            f += UrlFinding("REMOTE_PAYLOAD", "Path serves a downloadable app/installer - classic dropper", 40)
        }

        if (parsed.port.isNotEmpty() && parsed.port !in listOf("80", "443", "8080")) {
            f += UrlFinding("ODD_PORT", "Unusual port :${parsed.port}", 10)
        }
    }

    /** Returns the brand a confusable label appears to be imitating. */
    /**
     * Returns the brand a confusable label appears to be imitating.
     *
     * The plain label is NOT added to the variant set. "cybercrime.gov.in"
     * startsWith the brand token "cyber", but that is the genuine name, not a
     * homograph -- including it produced a false positive on the official
     * cybercrime portal. Only MUTATED forms count as evidence.
     */
    private fun confusableHits(label: String): String? {
        val lowered = label.lowercase(Locale.ROOT)
        if (lowered.isEmpty()) return null
        val variants = mutableSetOf<String>()
        for ((from, to) in CONFUSABLES) {
            if (lowered.indexOf(from) >= 0) {
                variants.add(lowered.replace(from.toString(), to.substring(0, 1)))
            }
        }
        if (variants.isEmpty()) return null
        // A hit only counts if the label does NOT already equal the brand.
        // Require that the ORIGINAL label does not already start with the brand.
        // Without this, mutating "cybercrime" to "cybercrirne" still startsWith
        // "cyber" and the genuine cybercrime.gov.in portal gets flagged.
        val target = BRANDS.keys.firstOrNull { brand ->
            if (lowered.startsWith(brand) || lowered == brand) return@firstOrNull false
            variants.any { it == brand || it.startsWith(brand) }
        }
        return if (target != null && target != lowered) target else null
    }
    /** Shannon entropy per character; high values track DGA-style labels. */
    fun shannonEntropy(s: String): Double {
        if (s.isEmpty()) return 0.0
        return -s.groupingBy { it }.eachCount().values.sumOf { c ->
            val p = c.toDouble() / s.length
            p * (Math.log(p) / Math.log(2.0))
        }
    }
}











