package com.rakshaksetu.sdk

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Requires a real Application/Context for RakshakVoiceIntegritySDK.getInstance();
// without the Robolectric runner ApplicationProvider throws
// "No instrumentation registered!".
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RakshakVoiceIntegritySDKTest {

    @Test
    fun evaluateSession_emptySegments_returnsProceed() = runBlocking {
        // Test evaluateSession with empty list
        val verdict = RakshakVoiceIntegritySDK.getInstance(
            androidx.test.core.app.ApplicationProvider.getApplicationContext()
        ).evaluateSession(emptyList(), RakshakVoiceIntegritySDK.RiskScenario.STANDARD_CONSUMER)

        assertTrue(verdict.isAuthenticHuman)
        assertEquals(RakshakVoiceIntegritySDK.EnterpriseAction.ALLOW_PROCEED, verdict.recommendedAction)
    }
}
