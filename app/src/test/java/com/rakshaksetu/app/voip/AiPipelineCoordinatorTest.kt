package com.rakshaksetu.app.voip

import com.rakshaksetu.app.ai.AiPipelineCoordinator
import com.rakshaksetu.app.ai.WaldSprtAccumulator
import com.rakshaksetu.app.audio.SpscAudioRingBuffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Targeted Unit & Integration test for AiPipelineCoordinator in unified app.
 */
class AiPipelineCoordinatorTest {

    private lateinit var ringBuffer: SpscAudioRingBuffer
    private lateinit var coordinator: AiPipelineCoordinator

    @Before
    fun setUp() {
        ringBuffer = SpscAudioRingBuffer()
        coordinator = AiPipelineCoordinator(context = null, ringBuffer = ringBuffer)
    }

    @Test
    fun testInitialUiStateIsSafe() {
        val state = coordinator.uiState.value
        assertEquals(AiPipelineCoordinator.ThreatLevel.SAFE, state.threatLevel)
        assertEquals(0.0f, state.sprtLambda, 0.001f)
        assertEquals(0.0f, state.threatPercent, 0.001f)
        assertFalse(state.isCallActive)
        assertFalse(state.isTamperSealed)
    }

    @Test
    fun testStartAndStopLifecycle() {
        coordinator.start()
        assertTrue(coordinator.uiState.value.isCallActive)

        coordinator.stop()
        assertFalse(coordinator.uiState.value.isCallActive)
    }

    @Test
    fun testBonafideVoiceSimulationMaintainsSafeState() {
        coordinator.simulateBonafideConversationalSpeech()
        val state = coordinator.uiState.value
        assertEquals(AiPipelineCoordinator.ThreatLevel.SAFE, state.threatLevel)
        assertEquals(0.0f, state.threatPercent, 0.001f)
        assertEquals(0.08f, state.cloneProbability, 0.001f)
        assertTrue(state.liveTranscript.contains("Namaste"))
    }

    @Test
    fun testTamperSealingMarker() {
        coordinator.markTamperSealed()
        assertTrue(coordinator.uiState.value.isTamperSealed)
    }

    @Test
    fun testResetClearsState() {
        coordinator.simulateBonafideConversationalSpeech()
        coordinator.markTamperSealed()
        assertTrue(coordinator.uiState.value.isTamperSealed)

        coordinator.reset()
        val resetState = coordinator.uiState.value
        assertEquals(AiPipelineCoordinator.ThreatLevel.SAFE, resetState.threatLevel)
        assertFalse(resetState.isTamperSealed)
        assertEquals(0, ringBuffer.available())
    }
}
