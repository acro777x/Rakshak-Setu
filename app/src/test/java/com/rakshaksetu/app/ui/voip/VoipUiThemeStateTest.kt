package com.rakshaksetu.app.ui.voip

import com.rakshaksetu.app.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.BlockedRed
import com.rakshaksetu.app.ui.theme.RakshakSetuBlue
import com.rakshaksetu.app.ui.theme.SafeGreen
import com.rakshaksetu.app.ui.theme.SuspiciousAmber
import org.junit.Assert.*
import org.junit.Test

class VoipUiThemeStateTest {

    @Test
    fun testScreenRoutesRegistered() {
        assertEquals("secure_line", Screen.SecureLine.route)
        assertEquals("voip_dialer", Screen.VoipDialer.route)
        assertEquals("voip_incoming_call/{callerId}", Screen.VoipIncomingCall.route)
        assertEquals("voip_active_hud/{peerId}", Screen.VoipActiveHud.route)
    }

    @Test
    fun testScreenRouteHelperUrlEncoding() {
        val callerRoute = Screen.VoipIncomingCall.createRoute("DCP Cyber Crime")
        assertTrue(callerRoute.startsWith("voip_incoming_call/"))
        assertTrue(callerRoute.contains("DCP") && (callerRoute.contains("+") || callerRoute.contains("%20")))

        val hudRoute = Screen.VoipActiveHud.createRoute("+91 98765 43210")
        assertTrue(hudRoute.startsWith("voip_active_hud/"))
    }

    @Test
    fun testThreatColorPaletteIntegrity() {
        // Confirm authentic RakshakSetuTheme palette constants
        assertEquals(androidx.compose.ui.graphics.Color(0xFF1565C0), RakshakSetuBlue)
        assertEquals(androidx.compose.ui.graphics.Color(0xFF2E7D32), SafeGreen)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFE65100), SuspiciousAmber)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFC62828), BlockedRed)
    }

    @Test
    fun testThreatLevelStateMapping() {
        val safeLevel = ThreatLevel.SAFE
        val evalLevel = ThreatLevel.EVALUATING
        val criticalLevel = ThreatLevel.CRITICAL_THREAT

        assertNotEquals(safeLevel, criticalLevel)
        assertNotEquals(evalLevel, criticalLevel)
    }
}
