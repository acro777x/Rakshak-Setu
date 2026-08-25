package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.FlaggedSegment
import com.rakshaksetu.app.model.PipelineMs
import java.io.File
import java.util.UUID

/**
 * Orchestrates: fetch -> decode -> segment -> VAD -> ASR -> analyzers ->
 * semantic matching -> voting + RL risk fusion -> evidence.
 *
 * Returns null ONLY when analysis genuinely could not run (no recording found /
 * decode failure). Callers must treat null as "no data", never as a scam signal.
 */
class PipelineCoordinator(
    private val context: Context,
    private val asrEngine: AsrEngine,
    private val votingEngine: VotingEngine,
    private val rlVotingEngine: RLVotingEngine = RLVotingEngine(context)
) {
    companion object {
        private const val TAG = "PipelineCoordinator"

        /** 
         * OEM call-recording directories probed as direct-path fallback.
         * Covers all major Android manufacturers (Samsung, Xiaomi/Redmi, TECNO/Infinix/Itel,
         * Vivo/iQOO, Oppo/Realme/OnePlus, Motorola, Nokia, Huawei/Honor, Google Pixel).
         */
        fun defaultOemPaths(): List<String> = listOf(
            // ── Transsion (TECNO / Infinix / Itel / HiOS) ──
            "/storage/emulated/0/Music/PhoneRecord",
            "/storage/emulated/0/PhoneRecord",

            // ── Samsung (One UI) ──
            "/storage/emulated/0/Recordings/Call",
            "/storage/emulated/0/Call",

            // ── Xiaomi / Redmi / POCO (MIUI / HyperOS) ──
            "/storage/emulated/0/MIUI/sound_recorder/call_rec",
            "/storage/emulated/0/MIUI/sound_recorder/call_recordings",

            // ── Vivo / iQOO (FuntouchOS / OriginOS) ──
            "/storage/emulated/0/Recordings/Call recordings",
            "/storage/emulated/0/Sounds/Call recordings",

            // ── Oppo / Realme / OnePlus (ColorOS / Realme UI / OxygenOS) ──
            "/storage/emulated/0/Music/Recordings/Call Recordings",
            "/storage/emulated/0/Recordings/Call Recordings",

            // ── OnePlus legacy ──
            "/storage/emulated/0/Android/data/com.oneplus.communication.data/files/Record/PhoneRecord",

            // ── Google Pixel / Stock Android (Google Dialer accessible recordings) ──
            "/storage/emulated/0/Android/data/com.google.android.dialer/files/Recordings",

            // ── Huawei / Honor (EMUI / MagicOS) ──
            "/storage/emulated/0/Sounds/CallRecord",
            "/storage/emulated/0/record",

            // ── Motorola / Nokia / Generic Android ──
            "/storage/emulated/0/Recordings",
            "/storage/emulated/0/Audio/Recordings",
            "/storage/emulated/0/Music/Recordings",
            "/storage/emulated/0/sound_recorder",
            "/storage/emulated/0/Music/Recordings/Call",
            "/sdcard/Recordings",
            "/sdcard/Recordings/Call"
        )
    }

    suspend fun runPipeline(
        callId: String = UUID.randomUUID().toString(),
        phoneNumber: String,
        callDurationSec: Int,
        callEndEpoch: Long,
        oemPaths: List<String> = defaultOemPaths(),
        destWavPath: String
    ): DetectionResult? {
        val tStart = System.currentTimeMillis()
        var tFetchEnd = 0L
        var tDecodeEnd = 0L
        var tAsrTotal = 0L
        var tEmbedTotal = 0L
        var tVoteTotal = 0L

        bootstrapEngines()

        // A1: Fetch (ContentObserver-driven wait for slow OEM media scanners)
        val audioUri = AudioFetcher.fetchWithRetries(context, callEndEpoch / 1000, oemPaths)
        if (audioUri == null) {
            Log.e(TAG, "No recording found for callId=$callId — pipeline abstains.")
            return null
        }
        tFetchEnd = System.currentTimeMillis()
        Log.i(TAG, "Recording located: $audioUri")

        // A2: Decode & normalize through content:// safe datasource
        val decodeSuccess = AudioDecoder.decodeToWav(context, audioUri, destWavPath)
        if (!decodeSuccess) {
            Log.e(TAG, "Failed to decode audio to WAV for callId=$callId.")
            return null
        }
        tDecodeEnd = System.currentTimeMillis()

        // A3/A4: Segment + VAD
        val segments = Segmenter.segmentAudio(File(destWavPath))
        Log.i(TAG, "${segments.size} raw segments extracted.")

        val fullTranscriptBuilder = StringBuilder()
        val processedSegments = mutableListOf<SegmentResult>()

        var maxDeepfakeProb = 0f
        var maxStressScore = 0f
        var sawCallCenterNoise = false
        var similaritySum = 0f

        for (seg in segments) {
            if (!VadGate.isVoiceActive(seg.pcmData)) {
                continue
            }

            // A5: Offline ASR (Vosk) with acoustic gate fallback
            val tAsrStart = System.currentTimeMillis()
            val transcript = asrEngine.transcribe(seg.pcmData)
            tAsrTotal += System.currentTimeMillis() - tAsrStart

            if (transcript.isNotBlank()) {
                fullTranscriptBuilder.append(transcript).append(" ")
            }

            // P2 advanced acoustic checks (feed the RL tier even when transcript is empty)
            val isDeepfakeProb = VoiceCloneDetector.analyze(seg.pcmData)
            val acousticEnv = AcousticAnalyzer.analyze(seg.pcmData)
            val stressScore = EmotionAnalyzer.analyzeStress(seg.pcmData)

            if (isDeepfakeProb > maxDeepfakeProb) maxDeepfakeProb = isDeepfakeProb
            if (stressScore > maxStressScore) maxStressScore = stressScore
            if (acousticEnv == AcousticAnalyzer.Environment.CALL_CENTER) sawCallCenterNoise = true

            if (isDeepfakeProb > 0.8f) {
                Log.w(TAG, "Deepfake / voice clone artifacts detected (p=$isDeepfakeProb)")
            }
            if (sawCallCenterNoise && seg.index % 5 == 0) {
                Log.w(TAG, "Acoustic environment matches call center.")
            }
            if (stressScore > 0.7f) {
                Log.w(TAG, "High victim stress detected (${(stressScore * 100).toInt()}%).")
            }

            // A7: Semantic matching (ONNX MiniLM when available, calibrated lexical otherwise)
            val tEmbedStart = System.currentTimeMillis()
            val (similarity, matchedCategory) =
                EmbeddingEngine.findBestMatch(transcript.ifBlank { "" })
            tEmbedTotal += System.currentTimeMillis() - tEmbedStart

            var finalSimilarity = if (transcript.isBlank()) 0f else similarity
            if (finalSimilarity > 0f &&
                (isDeepfakeProb > 0.8f || acousticEnv == AcousticAnalyzer.Environment.CALL_CENTER)
            ) {
                finalSimilarity = (finalSimilarity + 0.15f).coerceAtMost(1.0f)
            }

            processedSegments.add(
                SegmentResult(
                    index = seg.index,
                    startSec = seg.startSec,
                    text = transcript.trim(),
                    similarity = finalSimilarity,
                    matchedCategory = matchedCategory
                )
            )
            if (finalSimilarity > 0f) similaritySum += finalSimilarity
        }

        Log.i(TAG, "${processedSegments.size} voice-active segments processed.")

        // A8: Two-tier verdict — semantic voting ∪ RL acoustic risk
        val tVoteStart = System.currentTimeMillis()
        val verdict = votingEngine.evaluate(processedSegments)

        val avgSimilarity = if (processedSegments.isNotEmpty()) {
            similaritySum / processedSegments.size
        } else 0f

        val rlRiskScore = rlVotingEngine.score(avgSimilarity, maxDeepfakeProb, maxStressScore, sawCallCenterNoise)
        val rlConvicts = rlVotingEngine.evaluate(avgSimilarity, maxDeepfakeProb, maxStressScore, sawCallCenterNoise)
        tVoteTotal += System.currentTimeMillis() - tVoteStart

        val finalIsScam = verdict.isScam || rlConvicts
        val finalScamType = verdict.scamType
            ?: processedSegments.filter { it.matchedCategory != null }
                .maxByOrNull { it.similarity }?.matchedCategory
            ?: if (rlConvicts) "acoustic_anomaly" else null
        val finalConfidence = when {
            verdict.isScam -> verdict.confidence
            rlConvicts -> rlRiskScore.coerceIn(0f, 0.99f)
            else -> maxOf(verdict.confidence, 0f)
        }

        Log.i(
            TAG,
            "Verdict: voting=${verdict.isScam}(conf=%.2f) rl=$rlConvicts(score=%.2f) => scam=$finalIsScam".format(
                verdict.confidence, rlRiskScore
            )
        )

        // A9: Frozen DetectionResult contract
        val flaggedSegments = (if (verdict.isScam) verdict.hits else emptyList()).map {
            FlaggedSegment(
                index = it.index,
                startSec = it.startSec,
                text = it.text,
                similarity = it.similarity,
                matchedCategory = it.matchedCategory ?: "unknown"
            )
        }

        val pipelineMs = PipelineMs(
            fetch = tFetchEnd - tStart,
            decode = tDecodeEnd - tFetchEnd,
            asr = tAsrTotal,
            embed = tEmbedTotal,
            vote = tVoteTotal
        )

        val detectionResult = DetectionResult(
            callId = callId,
            phoneNumber = phoneNumber,
            callEndEpoch = callEndEpoch,
            durationSec = callDurationSec,
            audioUri = audioUri.toString(),
            isScam = finalIsScam,
            confidence = finalConfidence,
            scamType = finalScamType,
            flaggedSegments = flaggedSegments,
            fullTranscript = fullTranscriptBuilder.toString().trim(),
            pipelineMs = pipelineMs
        )

        // Phase 4: local threat intel + forensic manifest (scam only)
        if (detectionResult.isScam) {
            ThreatIntelClient.reportThreat(context, detectionResult)
            EvidenceManager.generateEvidencePackage(context, detectionResult)

            val topHitCategory = flaggedSegments.firstOrNull()?.matchedCategory
            if (topHitCategory != null && topHitCategory != "unknown") {
                FederatedLearningManager.logTruePositive(topHitCategory)
            }
        }

        return detectionResult
    }

    /**
     * One-time per-process initialization of every dormant AI component.
     */
    private fun bootstrapEngines() {
        try {
            FederatedLearningManager.attach(context)
            ScamPhraseLibrary.loadFromAssets(context)
            EmbeddingEngine.ensureInitialized(
                context,
                ModelDownloadManager.validatedEmbeddingModelPath(context)
            )
            AcousticAnalyzer.init(context)
            VoiceCloneDetector.init(context)
        } catch (e: Exception) {
            Log.e(TAG, "Engine bootstrap incomplete — degraded mode active.", e)
        }
    }
}
