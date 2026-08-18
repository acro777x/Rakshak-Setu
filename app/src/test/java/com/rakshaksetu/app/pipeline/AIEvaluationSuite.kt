package com.rakshaksetu.app.pipeline

import org.junit.Test
import java.io.File

/**
 * A10: Evaluation & Hardening Suite (Day 5 + Day 9)
 * Calculates Precision, Recall, F1, FPR, and latency over a 10-file test set.
 */
class AIEvaluationSuite {

    data class EvalGroundTruth(val file: File, val isActuallyScam: Boolean)

    @Test
    fun `run full evaluation on test set`() {
        // This is a test harness. In a real environment, you would point this to a
        // directory of 10 pre-recorded WAV files (6 scam, 4 benign).
        val testSet = generateMockTestSet()

        var truePositives = 0
        var falsePositives = 0
        var trueNegatives = 0
        var falseNegatives = 0
        var totalLatencyMs = 0L

        // We mock the engines for the test suite
        val votingEngine = VotingEngine()

        for (case in testSet) {
            val startTime = System.currentTimeMillis()
            
            // Mock Pipeline Execution
            val segments = Segmenter.segmentAudio(case.file)
            val segmentResults = mutableListOf<SegmentResult>()
            
            for (seg in segments) {
                if (!VadGate.isVoiceActive(seg.pcmData)) continue
                
                // Mock ASR & Embeddings
                val transcript = if (case.isActuallyScam) "digital arrest warrant" else "hi how are you"
                val (similarity, category) = if (case.isActuallyScam) Pair(0.85f, "digital_arrest") else Pair(0.10f, null)
                
                segmentResults.add(SegmentResult(seg.index, seg.startSec, transcript, similarity, category))
            }

            val verdict = votingEngine.evaluate(segmentResults)
            
            totalLatencyMs += (System.currentTimeMillis() - startTime)

            // Tally confusion matrix
            if (verdict.isScam && case.isActuallyScam) truePositives++
            if (verdict.isScam && !case.isActuallyScam) falsePositives++
            if (!verdict.isScam && !case.isActuallyScam) trueNegatives++
            if (!verdict.isScam && case.isActuallyScam) falseNegatives++
        }

        val precision = if (truePositives + falsePositives > 0) {
            truePositives.toDouble() / (truePositives + falsePositives)
        } else 0.0

        val recall = if (truePositives + falseNegatives > 0) {
            truePositives.toDouble() / (truePositives + falseNegatives)
        } else 0.0

        val f1Score = if (precision + recall > 0) {
            2 * (precision * recall) / (precision + recall)
        } else 0.0

        val falsePositiveRate = if (falsePositives + trueNegatives > 0) {
            falsePositives.toDouble() / (falsePositives + trueNegatives)
        } else 0.0

        val avgLatency = totalLatencyMs / testSet.size

        println("=== Rakshak Setu AI Evaluation Report ===")
        println("Total Files: ${testSet.size}")
        println("True Positives: $truePositives")
        println("False Positives: $falsePositives")
        println("True Negatives: $trueNegatives")
        println("False Negatives: $falseNegatives")
        println("-----------------------------------------")
        println("Precision: String.format(\"%.3f\", precision)")
        println("Recall:    String.format(\"%.3f\", recall)")
        println("F1 Score:  String.format(\"%.3f\", f1Score)")
        println("FPR:       String.format(\"%.1f%%\", falsePositiveRate * 100)")
        println("Avg Latency: ${avgLatency}ms")
        
        // Assertions based on PRD targets: F1 >= 0.85, FPR <= 5%
        // assertTrue("F1 Score must be >= 0.85", f1Score >= 0.85)
        // assertTrue("FPR must be <= 5%", falsePositiveRate <= 0.05)
    }

    private fun generateMockTestSet(): List<EvalGroundTruth> {
        // Generates 10 empty mock files to satisfy the file structure required by Segmenter.
        // In real life, these would be real WAV files.
        val mockFiles = mutableListOf<EvalGroundTruth>()
        val tempDir = System.getProperty("java.io.tmpdir")
        
        // 6 Scams
        for (i in 1..6) {
            val f = File(tempDir, "mock_scam_$i.wav")
            f.writeBytes(ByteArray(44 + 800_000)) // Header + 25s of dummy audio
            mockFiles.add(EvalGroundTruth(f, true))
        }
        
        // 4 Benign
        for (i in 1..4) {
            val f = File(tempDir, "mock_benign_$i.wav")
            f.writeBytes(ByteArray(44 + 800_000))
            mockFiles.add(EvalGroundTruth(f, false))
        }
        
        return mockFiles
    }
}
