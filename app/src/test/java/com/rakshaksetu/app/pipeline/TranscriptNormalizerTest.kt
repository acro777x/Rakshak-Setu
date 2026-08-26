package com.rakshaksetu.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptNormalizerTest {

    @Test
    fun normalize_correctsBankingTerms() {
        val input = "aapka अकउंट block ho jayega kripya केवीसी complete kare aur ओतीपी bataye"
        val expected = "aapka अकाउंट block ho jayega kripya केवाईसी complete kare aur ओटीपी bataye"
        assertEquals(expected, TranscriptNormalizer.normalize(input))
    }

    @Test
    fun normalize_correctsAuthorityTerms() {
        val input = "main सीबीयाई inspector bol raha hoon, ट्राय se complaint aayi hai"
        val expected = "main सीबीआई inspector bol raha hoon, ट्राई se complaint aayi hai"
        assertEquals(expected, TranscriptNormalizer.normalize(input))
    }

    @Test
    fun normalize_correctsScamTools() {
        val input = "Play Store se एनीडेस्क download karo aur code batao"
        val expected = "Play Store se एनीडेस्क download karo aur code batao"
        assertEquals(expected, TranscriptNormalizer.normalize(input))
    }

    @Test
    fun normalize_preservesCleanText() {
        val input = "Hello this is a normal call about dinner tonight"
        assertEquals(input, TranscriptNormalizer.normalize(input))
    }
}
