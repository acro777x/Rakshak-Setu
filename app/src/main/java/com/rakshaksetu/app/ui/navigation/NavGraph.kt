package com.rakshaksetu.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rakshaksetu.app.ui.screens.*

@Composable
fun RakshakSetuNavGraph(startDestination: String = Screen.Splash.route) {
    val navController = rememberNavController()

    fun navigateTo(route: String) {
        navController.navigate(route)
    }

    fun goBack() {
        navController.popBackStack()
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ── ONBOARDING ────────────────────────────────────
        composable(Screen.Splash.route) {
            SplashScreen(onFinished = { alreadyOnboarded ->
                val target = if (alreadyOnboarded) Screen.Dashboard.route else Screen.BeforeLogin.route
                navController.navigate(target) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }
        composable(Screen.BeforeLogin.route) {
            BeforeLoginScreen(onGetStarted = { navigateTo(Screen.Login.route) })
        }
        composable(Screen.Login.route) {
            LoginScreen(onLoginSuccess = { navigateTo(Screen.Terms.route) })
        }
        composable(Screen.Terms.route) {
            TermsScreen(onAgree = { navigateTo(Screen.PermissionEducation.route) })
        }
        composable(Screen.PermissionEducation.route) {
            PermissionEducationScreen(onContinue = { navigateTo(Screen.SecurityTour.route) })
        }
        composable(Screen.SecurityTour.route) {
            SecurityTourScreen(onContinue = { navigateTo(Screen.BankSetup.route) })
        }
        composable(Screen.BankSetup.route) {
            BankSetupScreen(onContinue = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        // ── MAIN ──────────────────────────────────────────
        composable(Screen.Dashboard.route) {
            DashboardScreen(onNavigate = ::navigateTo)
        }
        composable(Screen.AlertCenter.route) {
            AlertCenterScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(Screen.RedAlert.route) {
            RedAlertScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(Screen.YellowAlert.route) {
            YellowAlertScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }

        // ── SCAN HUB ──────────────────────────────────────
        composable(Screen.ScanHub.route) {
            ScanHubScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(Screen.CallSecurity.route) {
            CallSecurityScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(Screen.LinkChecker.route) {
            LinkCheckerScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(Screen.QRScanner.route) {
            QRScannerScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(Screen.FileScanner.route) {
            FileScannerScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(Screen.ImageScanner.route) {
            ImageScannerScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }

        // ── REPORTS ───────────────────────────────────────
        composable(Screen.Reports.route) {
            ReportsScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(
            Screen.ReportDetails.route,
            arguments = listOf(navArgument("reportId") { type = NavType.StringType })
        ) { backStackEntry ->
            val reportId = backStackEntry.arguments?.getString("reportId") ?: ""
            ReportDetailsScreen(reportId = reportId, onNavigate = ::navigateTo, onBack = ::goBack)
        }

        // ── CYBERCRIME REPORT WIZARD ──────────────────────
        composable(Screen.ReportStep1.route) {
            ReportStep1Screen(onNext = { navigateTo(Screen.ReportStep2.route) }, onBack = ::goBack)
        }
        composable(Screen.ReportStep2.route) {
            ReportStep2Screen(onNext = { navigateTo(Screen.ReportStep3.route) }, onBack = ::goBack)
        }
        composable(Screen.ReportStep3.route) {
            ReportStep3Screen(onNext = { navigateTo(Screen.ReportStep4.route) }, onBack = ::goBack)
        }
        composable(Screen.ReportStep4.route) {
            ReportStep4Screen(onSubmit = { navigateTo(Screen.ReportSuccess.route) }, onBack = ::goBack)
        }
        composable(Screen.ReportSuccess.route) {
            ReportSuccessScreen(onDone = {
                navController.navigate(Screen.Dashboard.route) {
                    popUpTo(Screen.Dashboard.route) { inclusive = true }
                }
            })
        }

        // ── PROFILE ───────────────────────────────────────
        composable(Screen.Profile.route) {
            ProfileScreen(onNavigate = ::navigateTo, onBack = ::goBack)
        }
        composable(Screen.PersonalInfo.route) {
            PersonalInfoScreen(onBack = ::goBack)
        }
        composable(Screen.SecurityPrivacy.route) {
            SecurityPrivacyScreen(onBack = ::goBack)
        }
        composable(Screen.TrustedContacts.route) {
            TrustedContactsScreen(onBack = ::goBack)
        }
        composable(Screen.SavedItems.route) {
            SavedItemsScreen(onBack = ::goBack)
        }
        composable(Screen.NotificationSettings.route) {
            NotificationSettingsScreen(onBack = ::goBack)
        }
        composable(Screen.HelpSupport.route) {
            HelpSupportScreen(onBack = ::goBack)
        }
        composable(Screen.AboutRakshakSetu.route) {
            AboutRakshakSetuScreen(onBack = ::goBack)
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = ::goBack)
        }
    }
}
