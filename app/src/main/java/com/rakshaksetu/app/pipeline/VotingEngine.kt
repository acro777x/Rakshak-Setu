package com.rakshaksetu.app.pipeline

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

class VotingEngine(
    // Default fallback if simThreshold isn't provided dynamically
    private val defaultSimThreshold: Float = 0.80f,
    private val voteK: Int = 3,               // >=3 segments
    private val window: Int = 5               // of last 5
) {
    /**
     * Evaluates the segment results using A4 semantic voting and Federated Learning dynamic thresholds.
     */
    fun evaluate(segments: List<SegmentResult>): DetectionVerdict {
        if (segments.isEmpty()) {
            return DetectionVerdict(false, null, 0.0f, emptyList())
        }

        // We use the sliding window of the last 'window' segments
        val recent = segments.takeLast(window)
        
        // Filter hits by applying the dynamic FL threshold specific to the matched category
        val hits = recent.filter { segment ->
            if (segment.matchedCategory != null) {
                val dynamicThreshold = FederatedLearningManager.getThresholdForCategory(segment.matchedCategory)
                segment.similarity >= dynamicThreshold
            } else {
                false
            }
        }
        
        val isScam = hits.size >= voteK
        
        val scamType = if (hits.isNotEmpty()) {
            hits.groupingBy { it.matchedCategory!! }
                .eachCount()
                .maxByOrNull { it.value }?.key
        } else {
            null
        }
        
        val confidence = if (hits.isNotEmpty()) {
            hits.map { it.similarity }.average().toFloat()
        } else {
            0.0f
        }
        
        return DetectionVerdict(isScam, scamType, confidence, hits)
    }
}
