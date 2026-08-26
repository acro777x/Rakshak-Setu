package com.rakshaksetu.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VotingEngineFullWindowTest {

    private fun seg(index: Int, startSec: Int, sim: Float, category: String? = "upi_qr_scam") =
        SegmentResult(index, startSec, "text $index", sim, category)

    @Test
    fun `early scam hits in long call are no longer ignored`() {
        val engine = VotingEngine()
        val segments = buildList {
            // Scam script delivered at the START of a 20-segment call (legacy bug: only last 5 inspected)
            add(seg(0, 0, 0.90f))
            add(seg(1, 4, 0.88f))
            add(seg(2, 8, 0.91f))
            for (i in 3 until 20) add(seg(i, i * 4, 0.10f, null))
        }

        val verdict = engine.evaluate(segments)
        assertTrue("Early-window scam cluster must convict", verdict.isScam)
        assertEquals("upi_qr_scam", verdict.scamType)
        assertTrue(verdict.confidence > 0.85f)
        assertEquals(3, verdict.hits.size)
    }

    @Test
    fun `scattered hits across call trigger whole-call fallback`() {
        val engine = VotingEngine()
        val segments = listOf(
            seg(0, 0, 0.85f),
            seg(1, 4, 0.10f, null),
            seg(2, 8, 0.10f, null),
            seg(3, 12, 0.86f),
            seg(4, 16, 0.10f, null),
            seg(5, 20, 0.10f, null),
            seg(6, 24, 0.87f),
            seg(7, 28, 0.10f, null)
        )

        val verdict = engine.evaluate(segments)
        assertTrue("6+ total hits across call should convict via fallback", verdict.isScam)
    }

    @Test
    fun `two isolated hits never convict`() {
        val engine = VotingEngine()
        val segments = listOf(
            seg(0, 0, 0.85f),
            seg(1, 4, 0.10f, null),
            seg(2, 8, 0.10f, null),
            seg(3, 12, 0.10f, null),
            seg(4, 16, 0.86f),
            seg(5, 20, 0.10f, null),
            seg(6, 24, 0.10f, null),
            seg(7, 28, 0.10f, null),
            seg(8, 32, 0.10f, null),
            seg(9, 36, 0.10f, null)
        )

        val verdict = engine.evaluate(segments)
        assertFalse("Below voteK and below 2x fallback — must stay benign", verdict.isScam)
    }

    @Test
    fun `empty input stays benign`() {
        val verdict = VotingEngine().evaluate(emptyList())
        assertFalse(verdict.isScam)
        assertEquals(0.0f, verdict.confidence, 0.001f)
    }
}
