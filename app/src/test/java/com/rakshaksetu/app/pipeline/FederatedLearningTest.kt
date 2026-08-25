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
    fun `test export deltas clears local cache`() {
        FederatedLearningManager.logFalsePositive("test_cat")
        
        val exportedJson = FederatedLearningManager.exportDeltas("test_hash")
        assertTrue(exportedJson.contains("test_cat"))
        assertTrue(exportedJson.contains("test_hash"))
        
        // A second export immediately after should be empty
        val secondExportJson = FederatedLearningManager.exportDeltas("test_hash")
        assertTrue("Second export should contain no deltas", secondExportJson.contains("\"deltas\":[]"))
    }
}
