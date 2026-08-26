package com.rakshaksetu.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LexicalScamMatcherTest {

    @Test
    fun `identical phrase scores at ceiling`() {
        val phrase = "aap digital arrest ho gaye hain video call disconnect mat karna"
        val score = LexicalScamMatcher.score(phrase, phrase)
        assertTrue("Identical text should score >= 0.9, got $score", score >= 0.90f)
    }

    @Test
    fun `near-complete script match crosses the 0_80 vote gate`() {
        val phrase = "main CBI se inspector Sharma bol raha hoon aapke against warrant issue hua hai"
        val transcript = "hello sir main CBI se inspector Sharma bol raha hoon aapke against warrant issue hua hai suniye"
        val score = LexicalScamMatcher.score(transcript, phrase)
        assertTrue("Near-verbatim scam script should cross 0.80 gate, got $score", score >= 0.80f)
    }

    @Test
    fun `benign conversation scores far below gate`() {
        val phrase = "supreme court ne aapke arrest ka order diya hai abhi 50000 transfer karo bail ke liye"
        val transcript = "haan bhai kal match dekha kya raina ne chhakka maara tha maza aa gaya"
        val score = LexicalScamMatcher.score(transcript, phrase)
        assertTrue("Unrelated chat must stay below 0.5, got $score", score < 0.50f)
    }

    @Test
    fun `empty inputs are safe`() {
        assertEquals(0f, LexicalScamMatcher.score("", "some phrase"), 0.001f)
        assertEquals(0f, LexicalScamMatcher.score("some words", ""), 0.001f)
    }

    @Test
    fun `tokenization strips punctuation and case`() {
        val tokens = LexicalScamMatcher.tokenize("Hello, WORLD! digital-arrest.")
        assertEquals(listOf("hello", "world", "digital", "arrest"), tokens)
    }
}
