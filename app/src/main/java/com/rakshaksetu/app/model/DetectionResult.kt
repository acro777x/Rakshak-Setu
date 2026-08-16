package com.rakshaksetu.app.model

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

data class DetectionResult(
    val callId: String,
    val phoneNumber: String,
    val callEndEpoch: Long,
    val durationSec: Int,
    val audioUri: String,
    val isScam: Boolean,
    val confidence: Float,
    val scamType: String?,
    val flaggedSegments: List<FlaggedSegment>,
    val fullTranscript: String,
    val pipelineMs: PipelineMs
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): DetectionResult = Gson().fromJson(json, DetectionResult::class.java)
    }
}

data class FlaggedSegment(
    val index: Int,
    val startSec: Int,
    val text: String,
    val similarity: Float,
    val matchedCategory: String
)

data class PipelineMs(
    val fetch: Long,
    val decode: Long,
    val asr: Long,
    val embed: Long,
    val vote: Long
)
