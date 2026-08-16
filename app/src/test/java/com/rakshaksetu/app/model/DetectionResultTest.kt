package com.rakshaksetu.app.model

import com.rakshaksetu.app.debug.FakePipelineEmitter
import org.junit.Assert.*
import org.junit.Test

class DetectionResultTest {

    @Test
    fun `scamResult has isScam true`() {
        val result = FakePipelineEmitter.scamResult()
        assertTrue(result.isScam)
    }

    @Test
    fun `benignResult has isScam false`() {
        val result = FakePipelineEmitter.benignResult()
        assertFalse(result.isScam)
    }

    @Test
    fun `scamResult has non-empty flaggedSegments`() {
        val result = FakePipelineEmitter.scamResult()
        assertTrue(result.flaggedSegments.isNotEmpty())
    }

    @Test
    fun `confidence in valid range`() {
        val result = FakePipelineEmitter.scamResult()
        assertTrue(result.confidence in 0f..1f)
    }

    @Test
    fun `lowConfidence result has confidence between 0_6 and 0_8`() {
        val result = FakePipelineEmitter.lowConfidenceResult()
        assertTrue(result.confidence >= 0.6f && result.confidence < 0.8f)
    }

    @Test
    fun `JSON serialization roundtrip preserves data`() {
        val original = FakePipelineEmitter.scamResult()
        val json = original.toJson()
        val restored = DetectionResult.fromJson(json)
        assertEquals(original.callId, restored.callId)
        assertEquals(original.phoneNumber, restored.phoneNumber)
        assertEquals(original.isScam, restored.isScam)
        assertEquals(original.confidence, restored.confidence, 0.001f)
        assertEquals(original.scamType, restored.scamType)
        assertEquals(original.flaggedSegments.size, restored.flaggedSegments.size)
    }

    @Test
    fun `callEndEpoch is in seconds not millis`() {
        val result = FakePipelineEmitter.scamResult()
        // Epoch seconds should be around 1.7 billion in 2026, not 1.7 trillion
        assertTrue("callEndEpoch should be seconds, got ${result.callEndEpoch}",
            result.callEndEpoch < 10_000_000_000L)
    }
}
