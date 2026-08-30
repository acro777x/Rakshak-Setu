package com.rakshaksetu.app.debug

import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.FlaggedSegment
import com.rakshaksetu.app.model.PipelineMs
import java.util.UUID

/**
 * Testing & Simulation Scenarios for Rakshak Setu v2.0
 * Provides mock detection results for all major cybercrime & voice cloning threats.
 */
object FakePipelineEmitter {

    fun voiceCloneResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+919811002233",
        callEndEpoch = System.currentTimeMillis(),
        durationSec = 45,
        audioUri = "content://fake/audio/voice_clone",
        isScam = true,
        confidence = 0.94f,
        scamType = "ai_voice_kidnap",
        flaggedSegments = listOf(
            FlaggedSegment(1, 0, "Papa mera accident ho gaya hai police ne pakad liya", 0.95f, "ai_voice_kidnap"),
            FlaggedSegment(2, 5, "Turant 50000 rupaye is UPI par bhejo nahi toh jail bhej denge", 0.93f, "ai_voice_kidnap")
        ),
        fullTranscript = "Papa mera accident ho gaya hai police ne pakad liya hai. Ye inspector baat kar rahe hain turant 50000 rupaye is UPI par bhejo nahi toh mujhe lockup mein daal denge.",
        pipelineMs = PipelineMs(fetch = 400, decode = 300, asr = 1200, embed = 200, vote = 5)
    )

    fun digitalArrestResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+919876543210",
        callEndEpoch = System.currentTimeMillis(),
        durationSec = 142,
        audioUri = "content://fake/audio/digital_arrest",
        isScam = true,
        confidence = 0.91f,
        scamType = "digital_arrest",
        flaggedSegments = listOf(
            FlaggedSegment(1, 5, "aap digital arrest ho gaye hain", 0.94f, "digital_arrest"),
            FlaggedSegment(3, 15, "CBI investigation chal rahi hai aapke khilaf", 0.89f, "digital_arrest"),
            FlaggedSegment(5, 25, "paise transfer karo verification ke liye", 0.87f, "digital_arrest")
        ),
        fullTranscript = "Hello sir main CBI headquarters se Inspector Sharma bol raha hoon. Aapke Aadhaar card se 24 fake bank accounts khule hain aur illegal transactions hui hain. Aap par Supreme Court se digital arrest warrant issue hua hai. Turant sarkari verification account mein security deposit transfer karo.",
        pipelineMs = PipelineMs(fetch = 900, decode = 700, asr = 2200, embed = 300, vote = 10)
    )

    fun screenShareResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+919123456789",
        callEndEpoch = System.currentTimeMillis(),
        durationSec = 98,
        audioUri = "content://fake/audio/screen_share",
        isScam = true,
        confidence = 0.88f,
        scamType = "screen_share_scam",
        flaggedSegments = listOf(
            FlaggedSegment(1, 10, "Play Store se AnyDesk app download karo", 0.92f, "screen_share_scam"),
            FlaggedSegment(2, 20, "9 digit ka access code batao refund ke liye", 0.89f, "screen_share_scam")
        ),
        fullTranscript = "Sir main customer care se bol raha hoon. Aapka electricity bill refund pending hai. Play Store se AnyDesk ya TeamViewer app download karke 9 digit code share kijiye taaki hum system se verify kar sakein.",
        pipelineMs = PipelineMs(fetch = 500, decode = 400, asr = 1800, embed = 250, vote = 8)
    )

    fun kycFraudResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+919999888877",
        callEndEpoch = System.currentTimeMillis(),
        durationSec = 110,
        audioUri = "content://fake/audio/kyc_fraud",
        isScam = true,
        confidence = 0.86f,
        scamType = "kyc_fraud",
        flaggedSegments = listOf(
            FlaggedSegment(1, 10, "Aapka SBI account KYC expire ho gaya hai", 0.90f, "kyc_fraud"),
            FlaggedSegment(2, 30, "Aaj raat 9 baje tak OTP share karo nahi toh account permanent freeze hoga", 0.88f, "kyc_fraud")
        ),
        fullTranscript = "Dear customer, aapka bank account KYC expire ho chuka hai. RBI guidelines ke anusaar agar aaj update nahi kiya toh account permanent block ho jayega. Turant aane wala 6 digit OTP share kijiye.",
        pipelineMs = PipelineMs(fetch = 600, decode = 450, asr = 1600, embed = 200, vote = 8)
    )

    fun loanExtortionResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+918877665544",
        callEndEpoch = System.currentTimeMillis(),
        durationSec = 60,
        audioUri = "content://fake/audio/loan_extortion",
        isScam = true,
        confidence = 0.92f,
        scamType = "loan_extortion",
        flaggedSegments = listOf(
            FlaggedSegment(1, 5, "Aapki morphed photos contact list ko bhej denge", 0.95f, "loan_extortion"),
            FlaggedSegment(2, 15, "Turant 20000 rupaye do penalty ke", 0.91f, "loan_extortion")
        ),
        fullTranscript = "Tera loan overdue hai. Agar 10 minute mein 20000 rupaye nahi bheje toh teri morphed photos tere saare WhatsApp contacts aur rishtedaaron ko bhej denge.",
        pipelineMs = PipelineMs(fetch = 400, decode = 300, asr = 1400, embed = 220, vote = 6)
    )

    fun benignResult(): DetectionResult = DetectionResult(
        callId = UUID.randomUUID().toString(),
        phoneNumber = "+919876000000",
        callEndEpoch = System.currentTimeMillis(),
        durationSec = 52,
        audioUri = "content://fake/audio/safe_call",
        isScam = false,
        confidence = 0.08f,
        scamType = null,
        flaggedSegments = emptyList(),
        fullTranscript = "Haan bhai kaisa hai? Shaam ko badminton khelne chalna hai kya? Main 6 baje court par milta hoon.",
        pipelineMs = PipelineMs(fetch = 300, decode = 250, asr = 900, embed = 150, vote = 2)
    )

    fun scamResult(): DetectionResult = digitalArrestResult().copy(callEndEpoch = 1772370000L)

    fun lowConfidenceResult(): DetectionResult = digitalArrestResult().copy(confidence = 0.68f, callEndEpoch = 1772370000L)

}
