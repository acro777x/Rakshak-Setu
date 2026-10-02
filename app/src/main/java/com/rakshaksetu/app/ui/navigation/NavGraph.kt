package com.rakshaksetu.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rakshaksetu.app.ui.screens.*
import com.rakshaksetu.app.ui.voip.*
import com.rakshaksetu.app.webrtc.VoipSessionManager
import kotlinx.coroutines.launch

/**
 * Default WebSocket signalling relay.
 *
 * 10.0.2.2 is the Android emulator's alias for the host machine's loopback, so
 * `server/signaling_server.js` running on the developer's laptop is reachable
 * from an emulator with no configuration. A physical device needs the LAN IP
 * instead, which is why this is a constant that can be overridden at build time
 * rather than a hard-coded production host.
 */
private const val DEFAULT_SIGNALING_URL = "ws://10.0.2.2:8080"

@Composable
fun RakshakSetuNavGraph(
    startDestination: String = Screen.Splash.route,
    dynamicRoute: String? = null
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Single shared call session for the whole app. Constructed lazily, so a
    // user who never opens Secure Line never instantiates WebRTC.
    val voipSession = remember { VoipSessionManager.get(context) }

    androidx.compose.runtime.LaunchedEffect(dynamicRoute) {
        if (!dynamicRoute.isNullOrBlank()) {
            navController.navigate(dynamicRoute) {
                launchSingleTop = true
            }
        }
    }

    val topLevelRoutes = remember {
        setOf(
            Screen.Dashboard.route,
            Screen.ScanHub.route,
            Screen.SecureLine.route,
            Screen.Reports.route,
            Screen.Profile.route
        )
    }

    fun navigateTo(route: String) {
        if (route in topLevelRoutes) {
            navController.navigate(route) {
                popUpTo(Screen.Dashboard.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } else {
            navController.navigate(route) {
                launchSingleTop = true
            }
        }
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

        // ── SECURE LINE / SOVEREIGN VOIP TELEPHONY ─────────
        // Both routes now drive the REAL WebRTC session (VoipSessionManager)
        // instead of jumping straight to a HUD with a hard-coded peer name. A call
        // is only placed once signalling is connected; until then the dialer is
        // disabled and shows why.
        composable(Screen.SecureLine.route) {
            VoipDialerScreen(
                onInitiateCall = { destination ->
                    if (voipSession.uiState.value.state == VoipSessionManager.CallState.IDLE) {
                        scope.launch { voipSession.connect(DEFAULT_SIGNALING_URL) }
                    }
                    voipSession.startOutgoingCall(destination)
                    navigateTo(Screen.VoipActiveHud.createRoute(destination))
                },
                onSpeedDial1930 = {
                    navigateTo(Screen.VoipActiveHud.createRoute("1930"))
                },
                onNavigate = ::navigateTo
            )
        }
        composable(Screen.VoipDialer.route) {
            VoipDialerScreen(
                onInitiateCall = { destination ->
                    if (voipSession.uiState.value.state == VoipSessionManager.CallState.IDLE) {
                        scope.launch { voipSession.connect(DEFAULT_SIGNALING_URL) }
                    }
                    voipSession.startOutgoingCall(destination)
                    navigateTo(Screen.VoipActiveHud.createRoute(destination))
                },
                onSpeedDial1930 = {
                    navigateTo(Screen.VoipActiveHud.createRoute("1930"))
                },
                onNavigate = ::navigateTo
            )
        }
        composable(
            route = "voip_incoming_call/{callerId}",
            arguments = listOf(navArgument("callerId") {
                type = NavType.StringType
                defaultValue = "DCP Cyber Crime (+91 98765 00001)"
            })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("callerId") ?: "DCP Cyber Crime (+91 98765 00001)"
            val callerId = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
            VoipIncomingCallScreen(
                callerId = callerId,
                onAcceptCall = {
                    navigateTo(Screen.VoipActiveHud.createRoute(callerId))
                },
                onDeclineCall = ::goBack
            )
        }
        composable("voip_incoming_call") {
            VoipIncomingCallScreen(
                callerId = "DCP Cyber Crime (+91 98765 00001)",
                onAcceptCall = {
                    navigateTo(Screen.VoipActiveHud.createRoute("DCP Cyber Crime"))
                },
                onDeclineCall = ::goBack
            )
        }
        composable(
            route = "voip_active_hud/{peerId}",
            arguments = listOf(navArgument("peerId") {
                type = NavType.StringType
                defaultValue = "+91 98765 43210"
            })
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString("peerId") ?: "+91 98765 43210"
            val peerId = try { java.net.URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
            VoipActiveCallHudScreen(
                peerId = peerId,
                onBack = ::goBack,
                onNavigate = ::navigateTo
            )
        }
        composable("voip_active_hud") {
            VoipActiveCallHudScreen(
                peerId = "+91 98765 43210",
                onBack = ::goBack,
                onNavigate = ::navigateTo
            )
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
