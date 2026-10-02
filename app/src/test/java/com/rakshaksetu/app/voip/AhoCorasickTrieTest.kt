package com.rakshaksetu.app.voip

import com.rakshaksetu.app.ai.AhoCorasickTrie
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Targeted Unit & Integration test for AhoCorasickTrie in unified app.
 */
class AhoCorasickTrieTest {

    private lateinit var trie: AhoCorasickTrie

    @Before
    fun setUp() {
        trie = AhoCorasickTrie()
        trie.seedDefaults()
    }

    @Test
    fun testAhoCorasickScamDetection() {
        val transcript = "Attention: Main CBI officer bol raha hoon, aapka digital arrest warrant jari ho gaya hai"
        val result = trie.scan(transcript)
        assertTrue(result.isThreat)
        assertTrue(result.riskScore >= 0.70f)
        assertTrue(result.matchedSpans.isNotEmpty())
    }

    @Test
    fun testAhoCorasickBenignSpeech() {
        val transcript = "Namaste, can we discuss the project roadmap tomorrow afternoon?"
        val result = trie.scan(transcript)
        assertFalse(result.isThreat)
        assertEquals(0.0f, result.riskScore, 0.0f)
        assertEquals("safe", result.topCategory)
    }

    @Test
    fun testAhoCorasickCustomPattern() {
        trie.insert("aadhaar kyc update link", "aadhaar_fraud", 0.95f)
        val result = trie.scan("Please click this aadhaar kyc update link immediately")
        assertTrue(result.isThreat)
        assertEquals("aadhaar_fraud", result.topCategory)
    }
}
