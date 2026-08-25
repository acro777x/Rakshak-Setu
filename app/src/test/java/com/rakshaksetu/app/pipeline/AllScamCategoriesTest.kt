package com.rakshaksetu.app.pipeline

import com.google.gson.Gson
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AllScamCategoriesTest {

    private lateinit var corpus: ScamPhraseCorpus

    @Before
    fun setUp() {
        val jsonFile = File("src/main/assets/scam_phrases.json")
        val jsonContent = if (jsonFile.exists()) {
            jsonFile.readText()
        } else {
            // Robolectric / Gradle working directory fallback
            File("app/src/main/assets/scam_phrases.json").readText()
        }
        corpus = Gson().fromJson(jsonContent, ScamPhraseCorpus::class.java)
    }

    @Test
    fun `corpus contains all 20 major scam categories`() {
        val expectedCategories = setOf(
            "digital_arrest",
            "ai_voice_kidnap",
            "screen_share_scam",
            "loan_extortion",
            "kyc_fraud",
            "esim_swap_5g",
            "govt_subsidy_phishing",
            "courier_customs",
            "electricity_bill",
            "trai_sim_block",
            "loan_lottery",
            "job_task_scam",
            "upi_qr_scam",
            "sextortion_blackmail",
            "crypto_investment",
            "traffic_challan",
            "pension_epfo",
            "gas_subsidy",
            "matrimonial_romance",
            "customer_care_poisoning"
        )

        val actualCategories = corpus.categories.map { it.id }.toSet()
        for (expected in expectedCategories) {
            assertTrue("Corpus should contain category $expected", actualCategories.contains(expected))
        }
    }

    @Test
    fun `every category has at least 5 distinct training phrases`() {
        for (category in corpus.categories) {
            assertTrue(
                "Category ${category.id} must have >= 5 phrases, found ${category.phrases.size}",
                category.phrases.size >= 5
            )
        }
    }

    @Test
    fun `lexical matcher scores above threshold for all 14 scam categories on verbatim phrases`() {
        for (category in corpus.categories) {
            for (phrase in category.phrases) {
                val score = LexicalScamMatcher.score(phrase, phrase)
                assertTrue(
                    "Verbatim phrase in ${category.id} should score >= 0.85, got $score for '$phrase'",
                    score >= 0.85f
                )
            }
        }
    }

    @Test
    fun `voting engine convicts across every scam category on simulated 3-segment call`() {
        val votingEngine = VotingEngine(defaultSimThreshold = 0.65f, voteK = 3)

        for (category in corpus.categories) {
            val samplePhrase = category.phrases.first()
            val segments = listOf(
                SegmentResult(0, 0, samplePhrase, 0.85f, category.id),
                SegmentResult(1, 5, samplePhrase, 0.88f, category.id),
                SegmentResult(2, 10, samplePhrase, 0.82f, category.id)
            )

            val verdict = votingEngine.evaluate(segments)
            assertTrue("VotingEngine should convict on ${category.id}", verdict.isScam)
            assertNotNull("ScamType must be present for ${category.id}", verdict.scamType)
            assertTrue("Confidence must be >= 0.80 for ${category.id}", verdict.confidence >= 0.80f)
        }
    }
}
