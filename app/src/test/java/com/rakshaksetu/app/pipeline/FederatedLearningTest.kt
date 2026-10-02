package com.rakshaksetu.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FederatedLearningTest {

    @Test
    fun `test false positive feedback increases threshold`() {
        val categoryId = "digital_arrest"
        
        // Initial threshold should be default (0.65)
        val initialThreshold = FederatedLearningManager.getThresholdForCategory(categoryId)
        assertEquals(0.65f, initialThreshold, 0.001f)

        // Log a false positive
        FederatedLearningManager.logFalsePositive(categoryId)

        // Threshold should increase by penalty step (0.02)
        val newThreshold = FederatedLearningManager.getThresholdForCategory(categoryId)
        assertEquals(0.67f, newThreshold, 0.001f)
    }

    @Test
    fun `test voting engine respects dynamic thresholds`() {
        val votingEngine = VotingEngine()
        val categoryId = "kyc_fraud"
        
        // Force a false positive adjustment for KYC fraud to push threshold to 0.67
        FederatedLearningManager.logFalsePositive(categoryId)
        
        // Create 3 segments with 0.66 similarity (just below the new 0.67 threshold, but above default 0.65)
        val segments = listOf(
            SegmentResult(0, 0, "kyc expire", 0.66f, categoryId),
            SegmentResult(1, 5, "account block", 0.66f, categoryId),
            SegmentResult(2, 10, "verify now", 0.66f, categoryId)
        )

        // With static 0.65 threshold, this would be a scam. 
        // With dynamic 0.67 threshold, this should NOT be a scam.
        val verdict = votingEngine.evaluate(segments)
        assertFalse("Verdict should not be scam due to dynamic threshold shift", verdict.isScam)
    }

    @Test
    fun `export is non-destructive until the server acknowledges`() {
        // FederatedLearningManager is an object singleton with a process-wide
        // delta queue, so drain it first rather than assume a clean slate.
        FederatedLearningManager.markDeltasExported()

        FederatedLearningManager.logFalsePositive("test_cat")
        assertEquals(1, FederatedLearningManager.pendingDeltaCount())

        val exportedJson = FederatedLearningManager.exportDeltas()
        assertTrue(exportedJson.contains("test_cat"))
        assertEquals(1, FederatedLearningManager.pendingDeltaCount())

        // Repeated exports must not lose the learning history when the upload
        // keeps failing offline — that was the previous destructive behaviour.
        val secondExportJson = FederatedLearningManager.exportDeltas()
        assertTrue(secondExportJson.contains("test_cat"))
        assertEquals(1, FederatedLearningManager.pendingDeltaCount())

        FederatedLearningManager.markDeltasExported()
        assertEquals(0, FederatedLearningManager.pendingDeltaCount())
        assertTrue(
            "After acknowledgement the queue is drained",
            FederatedLearningManager.exportDeltas().contains("\"deltas\":[]")
        )
    }

    @Test
    fun `export carries no device identifier`() {
        FederatedLearningManager.logFalsePositive("privacy_cat")
        val json = FederatedLearningManager.exportDeltas()

        // A hash is not anonymisation: Indian mobile numbers are only 10^10 and
        // are publicly enumerable, and the aggregator does not need to know which
        // client contributed what. No identifier may leave the device.
        assertFalse("payload must not carry a deviceIdHash", json.contains("deviceIdHash"))
        assertFalse("payload must not carry any hash field", json.contains("device_id"))
        assertFalse("payload must not carry a client id", json.contains("clientId"))
        assertTrue("payload still carries the delta itself", json.contains("privacy_cat"))
    }
}
