package com.safeshield.navigation

sealed class Screen(val route: String) {
    // Onboarding
    object Splash : Screen("splash")
    object BeforeLogin : Screen("before_login")
    object Login : Screen("login")
    object Terms : Screen("terms")
    object PermissionEducation : Screen("permission_education")
    object SecurityTour : Screen("security_tour")
    object BankSetup : Screen("bank_setup")

    // Main
    object Dashboard : Screen("dashboard")
    object AlertCenter : Screen("alert_center")
    object RedAlert : Screen("red_alert")
    object YellowAlert : Screen("yellow_alert")

    // Scan hub
    object ScanHub : Screen("scan_hub")
    object CallSecurity : Screen("call_security")
    object CallSecurityResult : Screen("call_security_result")
    object LinkChecker : Screen("link_checker")
    object LinkCheckerResult : Screen("link_checker_result")
    object QRScanner : Screen("qr_scanner")
    object QRScannerResult : Screen("qr_scanner_result")
    object FileScanner : Screen("file_scanner")
    object FileScannerResult : Screen("file_scanner_result")
    object ImageScanner : Screen("image_scanner")
    object ImageScannerResult : Screen("image_scanner_result")

    // Reports
    object Reports : Screen("reports")
    object ReportDetails : Screen("report_details/{reportId}") {
        fun createRoute(id: String) = "report_details/$id"
    }

    // Cybercrime report wizard
    object ReportStep1 : Screen("report_step1")
    object ReportStep2 : Screen("report_step2")
    object ReportStep3 : Screen("report_step3")
    object ReportStep4 : Screen("report_step4")
    object ReportSuccess : Screen("report_success")

    // Profile
    object Profile : Screen("profile")
    object PersonalInfo : Screen("personal_info")
    object SecurityPrivacy : Screen("security_privacy")
    object TrustedContacts : Screen("trusted_contacts")
    object SavedItems : Screen("saved_items")
    object NotificationSettings : Screen("notification_settings")
    object HelpSupport : Screen("help_support")
    object AboutSafeShield : Screen("about_safeshield")
    object Settings : Screen("settings")
}
