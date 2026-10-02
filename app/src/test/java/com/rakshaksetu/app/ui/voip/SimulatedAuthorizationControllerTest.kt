package com.rakshaksetu.app.ui.voip

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedAuthorizationControllerTest {

    @Test
    fun testInitialStateIsCountingAt10Seconds() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val controller = SimulatedAuthorizationController(initialSeconds = 10.0f, coroutineScope = testScope)

        assertEquals(10.0f, controller.remainingSeconds.value, 0.001f)
        assertEquals(AuthorizationStatus.COUNTING, controller.status.value)
        assertEquals("₹50,000", controller.state.value.amount)
        assertFalse(controller.state.value.alertBuzzTriggered)
        assertNull(controller.state.value.frozenAtSeconds)
    }

    @Test
    fun testGenuineSpeechCountdownCompletesToApproved() = runTest {
        var isApprovedCalled = false
        val controller = SimulatedAuthorizationController(initialSeconds = 1.0f, coroutineScope = this)

        controller.start(
            scope = this,
            onApproved = { isApprovedCalled = true }
        )

        assertEquals(AuthorizationStatus.COUNTING, controller.status.value)

        // Advance past 1.0s countdown (1100ms)
        testScheduler.advanceTimeBy(1100L)

        assertEquals(0.0f, controller.remainingSeconds.value, 0.05f)
        assertEquals(AuthorizationStatus.APPROVED, controller.status.value)
        assertTrue(isApprovedCalled)
        assertFalse(controller.state.value.alertBuzzTriggered)
    }

    @Test
    fun testClonedSpeechBreachImmediatelyFreezesCountdown() = runTest {
        var severCalled = false
        val controller = SimulatedAuthorizationController(initialSeconds = 10.0f, coroutineScope = this)

        controller.start(
            scope = this,
            onApproved = { fail("Should not be approved on clone threat!") },
            onSeverCall = { severCalled = true }
        )

        // Let countdown run for 2.3 seconds (~7.7s remaining)
        testScheduler.advanceTimeBy(2300L)

        val remainingBeforeFreeze = controller.remainingSeconds.value
        assertTrue("Remaining should be around 7.7s, was $remainingBeforeFreeze", remainingBeforeFreeze in 7.5f..7.9f)

        // Trigger Wald SPRT threat breach
        controller.freezeOnThreat(context = null) {
            severCalled = true
        }

        assertEquals(AuthorizationStatus.FROZEN, controller.status.value)
        assertTrue(controller.state.value.alertBuzzTriggered)
        assertNotNull(controller.state.value.frozenAtSeconds)
        assertEquals(remainingBeforeFreeze, controller.remainingSeconds.value, 0.05f)

        // Advance more time to confirm countdown remains frozen
        testScheduler.advanceTimeBy(5000L)
        assertEquals(remainingBeforeFreeze, controller.remainingSeconds.value, 0.05f)
        assertEquals(AuthorizationStatus.FROZEN, controller.status.value)

        // Sever call is invoked
        assertTrue(severCalled)
    }

    @Test
    fun testFreezeIdempotence() = runTest {
        val controller = SimulatedAuthorizationController(initialSeconds = 10.0f, coroutineScope = this)
        controller.start(scope = this)

        testScheduler.advanceTimeBy(2000L)
        controller.freezeOnThreat(context = null)

        val firstFrozen = controller.remainingSeconds.value
        assertEquals(AuthorizationStatus.FROZEN, controller.status.value)

        // Secondary call to freeze should not change the state or frozen timestamp
        controller.freezeOnThreat(context = null)
        assertEquals(firstFrozen, controller.remainingSeconds.value, 0.01f)
    }

    @Test
    fun testResetRestoresInitialCountingState() = runTest {
        val controller = SimulatedAuthorizationController(initialSeconds = 10.0f, coroutineScope = this)
        controller.start(scope = this)

        testScheduler.advanceTimeBy(3000L)
        controller.freezeOnThreat(context = null)
        assertEquals(AuthorizationStatus.FROZEN, controller.status.value)

        controller.reset()
        assertEquals(10.0f, controller.remainingSeconds.value, 0.001f)
        assertEquals(AuthorizationStatus.COUNTING, controller.status.value)
        assertFalse(controller.state.value.alertBuzzTriggered)
        assertNull(controller.state.value.frozenAtSeconds)
    }
}
