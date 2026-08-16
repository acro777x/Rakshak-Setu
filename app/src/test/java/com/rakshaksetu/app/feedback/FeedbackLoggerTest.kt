package com.rakshaksetu.app.feedback

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class FeedbackLoggerTest {

    private val gson = Gson()

    @Test
    fun `FeedbackEntry serializes to JSON correctly`() {
        val entry = FeedbackEntry(
            callId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            feedback = "not_scam",
            reason = "User correction"
        )
        val json = gson.toJson(entry)
        assertTrue(json.contains("not_scam"))
        assertTrue(json.contains(entry.callId))
    }

    @Test
    fun `FeedbackEntry deserializes from JSON correctly`() {
        val callId = UUID.randomUUID().toString()
        val json = """{"callId":"$callId","timestamp":1234567890,"feedback":"confirmed_scam","reason":null}"""
        val entry = gson.fromJson(json, FeedbackEntry::class.java)
        assertEquals(callId, entry.callId)
        assertEquals("confirmed_scam", entry.feedback)
        assertNull(entry.reason)
    }

    @Test
    fun `FeedbackEntry roundtrip preserves data`() {
        val original = FeedbackEntry(
            callId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            feedback = "not_scam",
            reason = "Was my friend calling"
        )
        val json = gson.toJson(original)
        val restored = gson.fromJson(json, FeedbackEntry::class.java)
        assertEquals(original.callId, restored.callId)
        assertEquals(original.feedback, restored.feedback)
        assertEquals(original.reason, restored.reason)
    }
}
