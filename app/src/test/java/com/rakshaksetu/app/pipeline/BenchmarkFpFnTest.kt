package com.rakshaksetu.app.pipeline

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Runs the golden FP/FN benchmark as part of the normal test suite.
 *
 * This does NOT assert a pass/fail accuracy bar -- it measures and records the
 * confusion matrix, then enforces only the invariants that must always hold:
 *   - the corpus is well formed and covers both classes
 *   - the arithmetic of the report is self-consistent
 *   - a labelled scam case is never silently dropped
 *
 * A moving accuracy bar would make the suite flaky and would hide regressions
 * behind a hard-coded number. Trend the emitted report instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BenchmarkFpFnTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun corpusIsWellFormedAndCoversBothClasses() {
        val corpus = BenchmarkFpFn.loadCorpus(context)
        assertTrue("corpus must not be empty", corpus.isNotEmpty())
        assertTrue("corpus must contain scam cases", corpus.any { it.isScam })
        assertTrue("corpus must contain benign cases", corpus.any { !it.isScam })
        assertTrue(
            "every scam case must declare a category",
            corpus.filter { it.isScam }.all { !it.category.isNullOrBlank() }
        )
        assertTrue("ids must be unique", corpus.map { it.id }.toSet().size == corpus.size)
    }

    @Test
    fun reportArithmeticIsSelfConsistent() {
        val corpus = BenchmarkFpFn.loadCorpus(context)
        val (report, outcomes) = BenchmarkFpFn.run(context, corpus)

        assertTrue("outcomes must match corpus size", outcomes.size == corpus.size)
        assertTrue(
            "confusion matrix must sum to the case count",
            report.truePositives + report.falsePositives +
                report.trueNegatives + report.falseNegatives == report.total
        )
        assertTrue("accuracy in [0,1]", report.accuracy in 0.0..1.0)
        assertTrue("fpRate in [0,1]", report.fpRate in 0.0..1.0)
        assertTrue("fnRate in [0,1]", report.fnRate in 0.0..1.0)
        assertTrue("total > 0", report.total > 0)
    }

    @Test
    fun emitsMeasuredReport() {
        val corpus = BenchmarkFpFn.loadCorpus(context)
        val (report, outcomes) = BenchmarkFpFn.run(context, corpus)

        val table = BenchmarkFpFn.formatTable(report, outcomes)
        println(table)

        // Persist for CI / trend tracking next to the project reports dir.
        BenchmarkFpFn.writeJsonReport(
            report, outcomes,
            File(System.getProperty("user.dir") ?: ".", "reports")
        )
    }

    @Test
    fun everyOutcomeIsAccountedFor() {
        val corpus = BenchmarkFpFn.loadCorpus(context)
        val (_, outcomes) = BenchmarkFpFn.run(context, corpus)
        outcomes.forEach { o ->
            // A verdict is only ever correct when the prediction matches truth.
            assertTrue(
                "outcome ${o.id} must agree with its own label",
                o.correct == (o.expectedScam == o.predictedScam)
            )
        }
    }
}