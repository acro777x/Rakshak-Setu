package com.rakshaksetu.app.elder

import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.FlaggedSegment
import com.rakshaksetu.app.model.PipelineMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmergencyDispatcherTest {

    private fun result(confidence: Float, isScam: Boolean = true) = DetectionResult(
        callId = "3fa85f64-5717-4562-b3fc-2c963f66afa6",
        phoneNumber = "+919876543210",
        callEndEpoch = System.currentTimeMillis() / 1000,
        durationSec = 120,
        audioUri = "content://media/external/audio/media/42",
        isScam = isScam,
        confidence = confidence,
        scamType = "digital_arrest",
        flaggedSegments = listOf(
            FlaggedSegment(0, 0, "digital arrest ho gaye", 0.9f, "digital_arrest")
        ),
        fullTranscript = "aap digital arrest ho gaye hain",
        pipelineMs = PipelineMs(1, 1, 1, 1, 1)
    )

    @Test
    fun `auto-send requires at least 85 percent confidence`() {
        assertFalse(EmergencyDispatcher.qualifiesForAutoSend(result(0.84f)))
        assertTrue(EmergencyDispatcher.qualifiesForAutoSend(result(0.86f)))
    }

    @Test
    fun `one-tap works from 60 percent`() {
        assertFalse(EmergencyDispatcher.qualifiesForOneTap(result(0.55f)))
        assertTrue(EmergencyDispatcher.qualifiesForOneTap(result(0.65f)))
    }

    @Test
    fun `benign verdicts never dispatch`() {
        assertFalse(EmergencyDispatcher.qualifiesForAutoSend(result(0.95f, isScam = false)))
        assertFalse(EmergencyDispatcher.qualifiesForOneTap(result(0.95f, isScam = false)))
    }

    @Test
    fun `sms body masks caller and stays within limits`() {
        val body = EmergencyDispatcher.buildSmsBody(result(0.92f))
        assertTrue(body.startsWith("EMERGENCY"))
        assertFalse("Full caller number must never leak in SMS", body.contains("+919876543210"))
        assertTrue(body.contains("XXXXXX3210"))
        assertTrue(body.length <= 480)
        assertTrue(body.contains("DIGITAL ARREST"))
    }

    @Test
    fun `guardian store validation`() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ElderModeStore(context)

        assertTrue(store.setGuardians(emptyList()))
        assertTrue(store.addGuardian(ElderModeStore.Guardian("Amma", "+91 98123 45678")))
        assertFalse("Duplicate number must be rejected", store.addGuardian(ElderModeStore.Guardian("Dup", "9812345678")))
        assertFalse("Too-short number must be rejected", store.addGuardian(ElderModeStore.Guardian("Bad", "12345")))
        assertEquals(1, store.getGuardians().size)

        assertTrue(store.removeGuardian("9812345678"))
        assertEquals(0, store.getGuardians().size)
    }
}
