package com.rakshaksetu.app.pipeline

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Golden FP/FN benchmark harness.
 *
 * Measures the two errors that matter for an anti-scam product:
 *
 *   False Positive (FP): a benign call flagged as a scam. For an elder-safety
 *   app this is the worst error -- it cries wolf at the user's own family and
 *   can trigger an automatic guardian SMS.
 *
 *   False Negative (FN): a real scam call not flagged. The user gets no warning
 *   while being defrauded.
 *
 * DESIGN RULES
 *  - Text-only by design. It exercises the path that actually runs today
 *    (TranscriptNormalizer -> EmbeddingEngine/LexicalScamMatcher ->
 *    ScamEngineFallback -> VotingEngine), which needs no ONNX model, no Vosk
 *    model and no device. Those are runtime downloads absent in CI, so an
 *    audio-only harness would silently measure nothing.
 *  - Corpus is versioned alongside the tests.
 *  - Deterministic: no network, no permissions, same input -> same verdict.
 *
 * HONESTY NOTE: a passing score here means the TEXT pipeline works. It is NOT
 * an end-to-end accuracy claim for voice-clone detection, which needs the real
 * ONNX detector.
 */
object BenchmarkFpFn {

    const val CORPUS_ASSET = "benchmark_corpus.json"

    data class Case(
        val id: String,
        val isScam: Boolean,
        val category: String? = null,
        val transcript: String
    )

    data class Outcome(
        val id: String,
        val expectedScam: Boolean,
        val predictedScam: Boolean,
        val category: String?,
        val confidence: Float,
        val tier: String
    ) {
        val correct: Boolean get() = expectedScam == predictedScam
    }

    data class Report(
        val total: Int,
        val truePositives: Int,
        val falsePositives: Int,
        val trueNegatives: Int,
        val falseNegatives: Int
    ) {
        /** Benign calls wrongly flagged. Lower is better. */
        val fpRate: Double
            get() = if ((trueNegatives + falsePositives) == 0) 0.0
                    else falsePositives.toDouble() / (trueNegatives + falsePositives)

        /** Scam calls missed. Lower is better. */
        val fnRate: Double
            get() = if ((truePositives + falseNegatives) == 0) 0.0
                    else falseNegatives.toDouble() / (truePositives + falseNegatives)

        val accuracy: Double
            get() = if (total == 0) 0.0
                    else (truePositives + trueNegatives).toDouble() / total
    }

    fun loadCorpus(context: Context, file: File? = null): List<Case> {
        val json = if (file != null && file.exists()) file.readText()
        else context.assets.open(CORPUS_ASSET).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<Case>>() {}.type
        return Gson().fromJson<List<Case>>(json, type) ?: emptyList()
    }
    // evaluate/run/formatTable/writeJsonReport follow below.

    /**
     * Runs one case through the live text pipeline, mirroring
     * PipelineCoordinator's decision order without needing audio.
     */
    fun evaluate(case: Case): Outcome {
        val transcript = TranscriptNormalizer.normalize(case.transcript).trim()
        val fallback = ScamEngineFallback.evaluate(transcript)

        val segments = transcript
            .split(Regex("(?<=[.!?।])\\s+"))
            .filter { it.isNotBlank() }
            .mapIndexed { i, seg ->
                val (sim, cat) = EmbeddingEngine.findBestMatch(seg)
                SegmentResult(
                    index = i,
                    startSec = i * 5,
                    text = seg,
                    similarity = if (seg.isBlank()) 0f else sim,
                    matchedCategory = cat
                )
            }

        val verdict = VotingEngine().evaluate(segments)
        val fallbackConvicts = fallback.isScam && fallback.confidence >= 0.70f
        val predicted = verdict.isScam || fallbackConvicts

        val tier = when {
            fallbackConvicts -> fallback.tierUsed
            verdict.isScam -> "VOTING_ENGINE"
            else -> "BENIGN"
        }
        val confidence = when {
            fallbackConvicts -> fallback.confidence
            verdict.isScam -> verdict.confidence
            else -> 0f
        }

        return Outcome(
            id = case.id,
            expectedScam = case.isScam,
            predictedScam = predicted,
            category = case.category ?: verdict.scamType ?: fallback.category,
            confidence = confidence,
            tier = tier
        )
    }

    /** Runs the whole corpus and tallies the confusion matrix. */
    fun run(context: Context, corpus: List<Case> = loadCorpus(context)): Pair<Report, List<Outcome>> {
        val outcomes = corpus.map { evaluate(it) }
        val report = Report(
            total = corpus.size,
            truePositives = outcomes.count { it.expectedScam && it.predictedScam },
            falsePositives = outcomes.count { !it.expectedScam && it.predictedScam },
            trueNegatives = outcomes.count { !it.expectedScam && !it.predictedScam },
            falseNegatives = outcomes.count { it.expectedScam && !it.predictedScam }
        )
        return report to outcomes
    }

    fun formatTable(report: Report, outcomes: List<Outcome>): String = buildString {
        appendLine("Rakshak Setu - Golden FP/FN Benchmark")
        appendLine("=".repeat(72))
        appendLine("Cases    : ${report.total}")
        appendLine("Accuracy : ${"%.2f".format(report.accuracy * 100)}%")
        appendLine("FP rate  : ${"%.2f".format(report.fpRate * 100)}%  (benign wrongly flagged)")
        appendLine("FN rate  : ${"%.2f".format(report.fnRate * 100)}%  (scams missed)")
        appendLine("-".repeat(72))
        appendLine("TP=${report.truePositives} FP=${report.falsePositives} " +
            "TN=${report.trueNegatives} FN=${report.falseNegatives}")
        appendLine("-".repeat(72))
        appendLine("Misclassified:")
        val wrong = outcomes.filter { !it.correct }
        if (wrong.isEmpty()) appendLine("  (none)")
        else wrong.forEach {
            appendLine("  [${if (it.expectedScam) "FN" else "FP"}] ${it.id} " +
                "conf=%.2f tier=${it.tier}".format(it.confidence))
            appendLine("        category=${it.category}")
        }
        appendLine("=".repeat(72))
    }

    /** Persists a machine-readable result for CI / trend tracking. */
    fun writeJsonReport(report: Report, outcomes: List<Outcome>, outDir: File) {
        outDir.mkdirs()
        val payload = JSONObject().apply {
            put("total", report.total)
            put("accuracy", report.accuracy)
            put("fpRate", report.fpRate)
            put("fnRate", report.fnRate)
            put("truePositives", report.truePositives)
            put("falsePositives", report.falsePositives)
            put("trueNegatives", report.trueNegatives)
            put("falseNegatives", report.falseNegatives)
            put("outcomes", JSONArray(Gson().toJson(outcomes)))
        }
        File(outDir, "benchmark_fp_fn.json").writeText(payload.toString(2))
    }
}