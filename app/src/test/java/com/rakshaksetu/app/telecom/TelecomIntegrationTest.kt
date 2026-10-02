package com.rakshaksetu.app.telecom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates AndroidManifest.xml configuration and TelecomManager integration contracts
 * for Android 14 VoIP and self-managed telephony compliance.
 */
class TelecomIntegrationTest {

    private fun loadManifestDocument(): Element {
        // Search relative to module directory or project root
        val possiblePaths = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("RakshakSetu/app/src/main/AndroidManifest.xml")
        )
        val manifestFile = possiblePaths.firstOrNull { it.exists() }
            ?: throw IllegalStateException("AndroidManifest.xml not found in candidates: $possiblePaths")

        val docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = docBuilder.parse(manifestFile)
        return doc.documentElement
    }

    @Test
    fun testAllRequiredVoipPermissionsDeclaredInManifest() {
        val root = loadManifestDocument()
        val permissionNodes = root.getElementsByTagName("uses-permission")
        val declaredPermissions = mutableSetOf<String>()

        for (i in 0 until permissionNodes.length) {
            val item = permissionNodes.item(i) as Element
            val name = item.getAttribute("android:name")
            if (name.isNotEmpty()) {
                declaredPermissions.add(name)
            }
        }

        val requiredPermissions = listOf(
            "android.permission.MANAGE_OWN_CALLS",
            "android.permission.READ_PHONE_STATE",
            "android.permission.CALL_PHONE",
            "android.permission.RECORD_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.USE_FULL_SCREEN_INTENT",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_PHONE_CALL",
            "android.permission.FOREGROUND_SERVICE_MICROPHONE",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.VIBRATE"
        )

        for (perm in requiredPermissions) {
            assertTrue("Missing required VoIP permission in manifest: $perm", declaredPermissions.contains(perm))
        }
    }

    @Test
    fun testTelecomConnectionServiceDeclaredWithProperFilterAndPermission() {
        val root = loadManifestDocument()
        val serviceNodes = root.getElementsByTagName("service")
        var connectionServiceElement: Element? = null

        for (i in 0 until serviceNodes.length) {
            val element = serviceNodes.item(i) as Element
            val name = element.getAttribute("android:name")
            if (name.endsWith("RakshakConnectionService")) {
                connectionServiceElement = element
                break
            }
        }

        assertNotNull("RakshakConnectionService declaration not found in AndroidManifest.xml", connectionServiceElement)
        val service = connectionServiceElement!!

        // Permission check
        assertEquals(
            "RakshakConnectionService must require BIND_TELECOM_CONNECTION_SERVICE permission",
            "android.permission.BIND_TELECOM_CONNECTION_SERVICE",
            service.getAttribute("android:permission")
        )

        // Must be exported to be bound by system TelecomManager
        assertEquals("RakshakConnectionService must be exported", "true", service.getAttribute("android:exported"))

        // Intent filter check for android.telecom.ConnectionService
        val filterNodes = service.getElementsByTagName("intent-filter")
        assertTrue("RakshakConnectionService must define an intent-filter", filterNodes.length > 0)

        var hasAction = false
        val actionNodes = (filterNodes.item(0) as Element).getElementsByTagName("action")
        for (i in 0 until actionNodes.length) {
            val action = actionNodes.item(i) as Element
            if (action.getAttribute("android:name") == "android.telecom.ConnectionService") {
                hasAction = true
                break
            }
        }
        assertTrue("RakshakConnectionService must include action android.telecom.ConnectionService", hasAction)
    }

    @Test
    fun testCallForegroundServiceDeclaredWithPhoneCallAndMicrophone() {
        val root = loadManifestDocument()
        val serviceNodes = root.getElementsByTagName("service")
        var callFgServiceElement: Element? = null

        for (i in 0 until serviceNodes.length) {
            val element = serviceNodes.item(i) as Element
            val name = element.getAttribute("android:name")
            if (name.endsWith("CallForegroundService")) {
                callFgServiceElement = element
                break
            }
        }

        assertNotNull("CallForegroundService declaration not found in AndroidManifest.xml", callFgServiceElement)
        val service = callFgServiceElement!!

        val fgType = service.getAttribute("android:foregroundServiceType")
        assertTrue(
            "CallForegroundService foregroundServiceType must include phoneCall (was: $fgType)",
            fgType.contains("phoneCall")
        )
        assertTrue(
            "CallForegroundService foregroundServiceType must include microphone (was: $fgType)",
            fgType.contains("microphone")
        )
        assertEquals("CallForegroundService should not be exported", "false", service.getAttribute("android:exported"))
    }

    @Test
    fun testTelecomConstantsAndNotificationContract() {
        assertEquals("voip_incoming_channel", IncomingCallNotificationManager.CHANNEL_INCOMING_ID)
        assertEquals("voip_ongoing_channel", IncomingCallNotificationManager.CHANNEL_ONGOING_ID)
        assertEquals(1001, IncomingCallNotificationManager.NOTIFICATION_ID_CALL)

        assertEquals("com.rakshaksetu.app.ACTION_ANSWER", IncomingCallNotificationManager.ACTION_ANSWER)
        assertEquals("com.rakshaksetu.app.ACTION_DECLINE", IncomingCallNotificationManager.ACTION_DECLINE)
        assertEquals("com.rakshaksetu.app.ACTION_HANGUP", IncomingCallNotificationManager.ACTION_HANGUP)
        assertEquals("extra_caller_id", IncomingCallNotificationManager.EXTRA_CALLER_ID)

        assertEquals("com.rakshaksetu.app.START_CALL", CallForegroundService.ACTION_START_CALL)
        assertEquals("com.rakshaksetu.app.STOP_CALL", CallForegroundService.ACTION_STOP_CALL)
        assertEquals("extra_peer_id", CallForegroundService.EXTRA_PEER_ID)
    }

    @Test
    fun testTelecomPhoneAccountIdContract() {
        assertEquals("rakshak_sovereign_voip_account", TelecomCallManager.PHONE_ACCOUNT_ID)
    }
}
