package com.safeshield.data

data class ActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String, // "call", "link", "file", "qr", "image"
    val status: RiskStatus,
    val timestamp: String
)

data class ReportItem(
    val id: String,
    val title: String,
    val type: String,
    val status: RiskStatus,
    val date: String,
    val description: String
)

data class TrustedContact(
    val id: String,
    val name: String,
    val relation: String,
    val phone: String
)

enum class RiskStatus { SAFE, SUSPICIOUS, BLOCKED, HIGH_RISK }

object MockData {

    val recentActivity = listOf(
        ActivityItem("a1", "Call from +91 98765 43210", "Voice cloning suspected", "call", RiskStatus.HIGH_RISK, "10:30 AM"),
        ActivityItem("a2", "www.free-gift.store", "Phishing link detected", "link", RiskStatus.BLOCKED, "09:05 AM"),
        ActivityItem("a3", "QR Code Scanned", "https://secure.nmrc.in", "qr", RiskStatus.SAFE, "01:10 AM"),
        ActivityItem("a4", "File: invoice.pdf", "No threats detected", "file", RiskStatus.SAFE, "Yesterday"),
        ActivityItem("a5", "Screenshot_2025.png", "Hidden QR code found", "image", RiskStatus.SUSPICIOUS, "Yesterday")
    )

    val reports = listOf(
        ReportItem("r1", "Call from +91 98765 43210", "Calls", RiskStatus.HIGH_RISK, "21 May 2025",
            "Caller used suspicious script patterns suggesting voice cloning with 82% confidence."),
        ReportItem("r2", "www.free-gift.store", "Links", RiskStatus.BLOCKED, "21 May 2025",
            "Domain flagged as phishing. SSL cert expired, domain age < 2 days."),
        ReportItem("r3", "+91 98765 43210", "Calls", RiskStatus.HIGH_RISK, "31 May 2025",
            "High risk voice clone analysis result."),
        ReportItem("r4", "invoice.pdf", "Files", RiskStatus.SAFE, "31 May 2025",
            "File scanned. No known threats detected."),
        ReportItem("r5", "QR Code Scanned", "QR", RiskStatus.SAFE, "30 May 2025",
            "Safe link detected from QR code."),
        ReportItem("r6", "Photo_2025.png", "Images", RiskStatus.SUSPICIOUS, "30 May 2025",
            "Suspicious URL pattern found in image metadata.")
    )

    val trustedContacts = listOf(
        TrustedContact("c1", "Mom", "Family", "+91 88780 17111"),
        TrustedContact("c2", "Best Friend", "Friend", "+91 98765 32233"),
        TrustedContact("c3", "Brother", "Family", "+91 98765 50333")
    )

    val protectedCount = 128
    val suspiciousCount = 7
    val blockedCount = 3
    val filesScannedCount = 42

    val mockReferenceId = "SSREP-2025-00142"

    val trustedServices = listOf(
        Triple("NMRC Portal", "National cybercrime reporting", "https://cybercrime.gov.in"),
        Triple("Bank Mail Portal", "Phishing report to bank", "https://bankmail.portal.in"),
        Triple("Chakshu", "Report suspicious calls", "https://sancharsaathi.gov.in/citizen/Home/chakshu"),
        Triple("Call 1930", "Cybercrime helpline", "tel:1930")
    )
}
