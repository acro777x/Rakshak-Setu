package com.rakshaksetu.app.pipeline

import android.util.Log

data class SegmentResult(
    val index: Int,
    val startSec: Int,
    val text: String,
    val similarity: Float,
    val matchedCategory: String?
)

data class DetectionVerdict(
    val isScam: Boolean,
    val scamType: String?,
    val confidence: Float,
    val hits: List<SegmentResult>
)

/**
 * A4 semantic voting over the ENTIRE call.
 *
 * Legacy defect fixed: the old engine only inspected the last 5 segments, so a scam
 * script delivered in the first half of a long call was silently ignored. Now every
 * overlapping window of [window] segments is evaluated; a window convicts when it
 * contains >= [voteK] threshold-passing hits. A whole-call fallback additionally
 * convicts when total hits reach 2x voteK even if never clustered in one window.
 */
class VotingEngine(
    private val defaultSimThreshold: Float = 0.65f,
    private val voteK: Int = 3,
    private val window: Int = 5
) {
    companion object {
        private const val TAG = "VotingEngine"
    }

    fun evaluate(segments: List<SegmentResult>): DetectionVerdict {
        if (segments.isEmpty()) {
            return DetectionVerdict(false, null, 0.0f, emptyList())
        }

        val hits = segments.filter { segment ->
            if (segment.matchedCategory != null) {
                val dynamicThreshold =
                    FederatedLearningManager.getThresholdForCategory(segment.matchedCategory)
                val passes = segment.similarity >= dynamicThreshold
                Log.i(TAG, "Seg[${segment.index}] cat=${segment.matchedCategory} sim=%.3f thr=%.3f => pass=$passes".format(segment.similarity, dynamicThreshold))
                passes
            } else {
                false
            }
        }

        val effectiveVoteK = when (segments.size) {
            1, 2 -> 1
            3, 4 -> minOf(2, voteK)
            else -> voteK
        }

        Log.i(TAG, "Total segments=${segments.size}, passing hits=${hits.size}, effectiveVoteK=$effectiveVoteK")

        if (hits.isEmpty()) {
            return DetectionVerdict(false, null, 0.0f, emptyList())
        }

        val hitIndices = hits.mapNotNull { segments.indexOf(it) }.toSet()

        var anyWindowPasses = false
        var i = 0
        while (i < segments.size && !anyWindowPasses) {
            val winEnd = minOf(i + window, segments.size)
            var count = 0
            for (j in i until winEnd) {
                if (j in hitIndices) count++
                if (count >= effectiveVoteK) break
            }
            if (count >= effectiveVoteK) anyWindowPasses = true
            i++
        }

        // Scattered-evidence fallback: for short calls, effectiveVoteK total hits anywhere convict;
        // long calls demand proportional evidence to stay precision-safe.
        val scatteredFallbackQuota = maxOf(effectiveVoteK, (segments.size * 0.15f).toInt())
        val isScam = anyWindowPasses || hits.size >= scatteredFallbackQuota

        val scamType = hits.groupingBy { it.matchedCategory!! }
            .eachCount()
            .maxByOrNull { it.value }?.key

        val confidence = hits.map { it.similarity }.average().toFloat().coerceIn(0f, 1f)

        return DetectionVerdict(isScam, scamType, confidence, hits)
    }
}
