package com.rakshaksetu.app.debug

import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.FlaggedSegment
import com.rakshaksetu.app.model.PipelineMs
import java.util.UUID

/**
 * Fake pipeline output for development/testing.
 * WARNING: Use only in BuildConfig.DEBUG context.
 * Real AI pipeline will replace this at integration (Day 4).
 */
object FakePipelineEmitter {

    fun scamResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+919876543210",
        callEndEpoch = System.currentTimeMillis() / 1000, // epoch SECONDS
        durationSec = 142,
        audioUri = "content://fake/audio/test", // Safe fake URI
        isScam = true,
        confidence = 0.87f,
        scamType = "digital_arrest",
        flaggedSegments = listOf(
            FlaggedSegment(1, 5, "aap digital arrest ho gaye hain", 0.92f, "digital_arrest"),
            FlaggedSegment(3, 15, "CBI investigation chal rahi hai aapke khilaf", 0.88f, "digital_arrest"),
            FlaggedSegment(5, 25, "paise transfer karo verification ke liye", 0.85f, "digital_arrest")
        ),
        fullTranscript = "Hello sir main CBI se bol raha hoon. Aapke Aadhaar card se kuch suspicious transactions hui hain. Aap digital arrest ho gaye hain. Video call par warrant dikhata hoon. Paise transfer karo verification ke liye.",
        pipelineMs = PipelineMs(fetch = 900, decode = 700, asr = 9200, embed = 600, vote = 10)
    )

    fun benignResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+911234567890",
        callEndEpoch = System.currentTimeMillis() / 1000,
        durationSec = 85,
        audioUri = "content://fake/audio/test_benign",
        isScam = false,
        confidence = 0.12f,
        scamType = null,
        flaggedSegments = emptyList(),
        fullTranscript = "Hello ji, aapka order dispatch ho gaya hai. Kal tak delivery ho jayegi. Thank you.",
        pipelineMs = PipelineMs(fetch = 800, decode = 600, asr = 8500, embed = 500, vote = 8)
    )

    fun lowConfidenceResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+919999888877",
        callEndEpoch = System.currentTimeMillis() / 1000,
        durationSec = 200,
        audioUri = "content://fake/audio/test_low",
        isScam = true,
        confidence = 0.65f,
        scamType = "kyc_fraud",
        flaggedSegments = listOf(
            FlaggedSegment(2, 10, "KYC update karna padega", 0.72f, "kyc_fraud"),
            FlaggedSegment(6, 30, "account band ho jayega", 0.68f, "kyc_fraud")
        ),
        fullTranscript = "Sir aapka KYC expire ho gaya hai. Agar aaj update nahi kiya toh account band ho jayega.",
        pipelineMs = PipelineMs(fetch = 950, decode = 750, asr = 11000, embed = 650, vote = 12)
    )
}
