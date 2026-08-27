package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.FlaggedSegment
import com.rakshaksetu.app.model.PipelineMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.File
import java.util.UUID

/**
 * Orchestrates Rakshak Setu Dual Parallel AI Pipeline:
 * - Engine A (async): Voice Clone / Deepfake Detection via SpectralFeatureExtractor + AASIST
 * - Engine B (async): Speech-to-Text ASR + TranscriptNormalizer + IntentPrototypeClassifier + EmbeddingEngine
 * - Multi-Signal Ensemble Voting: Phrase matching ∪ Intent prototypes ∪ Deepfake probability ∪ WeightedRiskScorer
 */
class PipelineCoordinator(
    private val context: Context,
    private val asrEngine: AsrEngine,
    private val votingEngine: VotingEngine,
    private val riskScorer: WeightedRiskScorer = WeightedRiskScorer(context)
) {
    companion object {
        private const val TAG = "PipelineCoordinator"

        /**
         * OEM call-recording directories probed as direct-path fallback.
         */
        fun defaultOemPaths(): List<String> = listOf(
            "/storage/emulated/0/Music/PhoneRecord",
            "/storage/emulated/0/PhoneRecord",
            "/storage/emulated/0/Recordings/Call",
            "/storage/emulated/0/Call",
            "/storage/emulated/0/MIUI/sound_recorder/call_rec",
            "/storage/emulated/0/MIUI/sound_recorder/call_recordings",
            "/storage/emulated/0/Recordings/Call recordings",
            "/storage/emulated/0/Sounds/Call recordings",
            "/storage/emulated/0/Music/Recordings/Call Recordings",
            "/storage/emulated/0/Recordings/Call Recordings",
            "/storage/emulated/0/Android/data/com.oneplus.communication.data/files/Record/PhoneRecord",
            "/storage/emulated/0/Android/data/com.google.android.dialer/files/Recordings",
            "/storage/emulated/0/Sounds/CallRecord",
            "/storage/emulated/0/record",
            "/storage/emulated/0/Recordings",
            "/storage/emulated/0/Audio/Recordings",
            "/storage/emulated/0/Music/Recordings",
            "/storage/emulated/0/sound_recorder",
            "/storage/emulated/0/Music/Recordings/Call",
            "/sdcard/Recordings",
            "/sdcard/Recordings/Call"
        )
    }

    private val cloneDetector = CloneDetectorEngine(context)
    private val intentClassifier = IntentPrototypeClassifier(context)
    suspend fun runPipeline(
        callId: String = UUID.randomUUID().toString(),
        phoneNumber: String,
        callDurationSec: Int,
        callEndEpoch: Long,
        oemPaths: List<String> = defaultOemPaths(),
        destWavPath: String
    ): DetectionResult? = coroutineScope {
        val tStart = System.currentTimeMillis()
        var tFetchEnd = 0L
        var tDecodeEnd = 0L
        var tAsrTotal = 0L
        var tEmbedTotal = 0L
        var tVoteTotal = 0L

        bootstrapEngines()

        // A1: Fetch (ContentObserver-driven wait for slow OEM media scanners)
        // OEM recorders stamp files at call START. Use callStartEpochSec to never miss long calls.
        val callStartEpochSec = ((callEndEpoch / 1000) - callDurationSec.coerceAtLeast(0)).coerceAtLeast(0L)
        val audioUri = AudioFetcher.fetchWithRetries(context, callStartEpochSec, oemPaths)
        if (audioUri == null) {
            Log.e(TAG, "No recording found for callId=$callId — pipeline abstains.")
            return@coroutineScope null
        }
        tFetchEnd = System.currentTimeMillis()
        Log.i(TAG, "Recording located: $audioUri")

        // A2: Decode & normalize through content:// safe datasource
        val decodeSuccess = AudioDecoder.decodeToWav(context, audioUri, destWavPath)
        if (!decodeSuccess) {
            Log.e(TAG, "Failed to decode audio to WAV for callId=$callId.")
            return@coroutineScope null
        }
        tDecodeEnd = System.currentTimeMillis()

        // A3/A4: Segment + VAD
        val segments = Segmenter.segmentAudio(File(destWavPath))
        Log.i(TAG, "${segments.size} raw segments extracted.")

        val activeSegments = segments.filter { VadGate.isVoiceActive(it.pcmData) }
        val pcmSegments = activeSegments.map { it.pcmData }

        // ============================================================
        // DUAL PARALLEL AI ENGINES RUNNING SIMULTANEOUSLY
        // ============================================================

        // ENGINE A (async): Voice Clone / Deepfake Detection
        val engineADeferred = async(Dispatchers.Default) {
            try {
                val t0 = System.currentTimeMillis()
                val result = cloneDetector.detectClone(pcmSegments)
                Log.i(TAG, "Engine A (CloneDetector) finished in ${System.currentTimeMillis() - t0}ms: isCloned=${result.isCloned}, conf=%.2f".format(result.confidence))
                result
            } catch (e: Exception) {
                Log.e(TAG, "Engine A encountered error; defaulting to safe fallback", e)
                CloneDetectorEngine.CloneResult(false, 0f, 0f, 0f, emptyList(), 0f, 1f)
            }
        }

        // ENGINE B (async): ASR Transcription + Normalization + Intent + Phrase Matching
        val engineBDeferred = async(Dispatchers.Default) {
            try {
                val fullTranscriptBuilder = StringBuilder()
                val processedSegments = mutableListOf<SegmentResult>()
                val segmentTranscripts = mutableListOf<String>()
                var maxLoudness = 0f
                var simSum = 0f

                for (seg in activeSegments) {
                    // A5: Offline ASR
                    val tAsrStart = System.currentTimeMillis()
                    val rawTranscript = asrEngine.transcribe(seg.pcmData)
                    tAsrTotal += System.currentTimeMillis() - tAsrStart

                    // Fuzzy transcription normalizer
                    val transcript = TranscriptNormalizer.normalize(rawTranscript).trim()

                    if (transcript.isNotBlank()) {
                        fullTranscriptBuilder.append(transcript).append(" ")
                        segmentTranscripts.add(transcript)
                    }

                    // Loudness
                    val loudness = LoudnessDetector.analyzeLoudness(seg.pcmData)
                    if (loudness > maxLoudness) maxLoudness = loudness

                    // Semantic phrase matching
                    val tEmbedStart = System.currentTimeMillis()
                    val (similarity, matchedCategory) = EmbeddingEngine.findBestMatch(transcript.ifBlank { "" })
                    tEmbedTotal += System.currentTimeMillis() - tEmbedStart

                    val finalSim = if (transcript.isBlank()) 0f else similarity
                    processedSegments.add(
                        SegmentResult(
                            index = seg.index,
                            startSec = seg.startSec,
                            text = transcript,
                            similarity = finalSim,
                            matchedCategory = matchedCategory
                        )
                    )
                    if (finalSim > 0f) simSum += finalSim
                }

                // Intent Prototype Classification over all transcripts
                val intentResult = intentClassifier.analyzeCall(segmentTranscripts)
                Log.i(TAG, "Engine B (IntentClassifier): isScam=${intentResult.isScam}, threats=${intentResult.distinctThreatCount}, dominant=${intentResult.dominantThreat}")

                EngineBResult(
                    fullTranscript = fullTranscriptBuilder.toString().trim(),
                    processedSegments = processedSegments,
                    segmentTranscripts = segmentTranscripts,
                    intentResult = intentResult,
                    maxLoudness = maxLoudness,
                    avgSimilarity = if (processedSegments.isNotEmpty()) simSum / processedSegments.size else 0f
                )
            } catch (e: Exception) {
                Log.e(TAG, "Engine B encountered error; returning empty transcript result", e)
                EngineBResult(
                    fullTranscript = "",
                    processedSegments = emptyList(),
                    segmentTranscripts = emptyList(),
                    intentResult = IntentPrototypeClassifier.CallIntentResult(false, emptyMap(), 0, emptyList()),
                    maxLoudness = 0f,
                    avgSimilarity = 0f
                )
            }
        }

        // Await results from both parallel engines
        val cloneResult = engineADeferred.await()
        val engineBResult = engineBDeferred.await()

        val fullTranscript = engineBResult.fullTranscript
        val processedSegments = engineBResult.processedSegments
        val intentResult = engineBResult.intentResult

        Log.i(TAG, "${processedSegments.size} voice-active segments processed.")
        Log.i(TAG, "Full transcript: '${fullTranscript.take(500)}'")

        // ============================================================
        // SAFETY-NET: ScamEngineFallback 3-Tier evaluation on full transcript
        // ============================================================
        val fallbackVerdict = if (fullTranscript.isNotBlank()) {
            try {
                ScamEngineFallback.evaluate(fullTranscript).also {
                    Log.i(TAG, "ScamEngineFallback: isScam=${it.isScam} conf=%.2f cat=${it.category} tier=${it.tierUsed}".format(it.confidence))
                }
            } catch (e: Exception) {
                Log.w(TAG, "ScamEngineFallback evaluation failed, skipping.", e)
                null
            }
        } else null

        // ============================================================
        // MULTI-SIGNAL ENSEMBLE DECISION
        // ============================================================
        val tVoteStart = System.currentTimeMillis()
        val ensembleVerdict = votingEngine.evaluateEnsemble(processedSegments, intentResult, cloneResult)

        val intentThreatScore = if (intentResult.isScam) intentResult.dominantThreatScore else 0f
        val weightedRisk = riskScorer.score(
            engineBResult.avgSimilarity,
            cloneResult.confidence,
            intentThreatScore,
            engineBResult.maxLoudness
        )
        val weightedConvicts = riskScorer.evaluate(
            engineBResult.avgSimilarity,
            cloneResult.confidence,
            intentThreatScore,
            engineBResult.maxLoudness
        )
        tVoteTotal += System.currentTimeMillis() - tVoteStart

        // ScamEngineFallback acts as safety-net: catches scams that slip past
        // EmbeddingEngine per-segment matching (e.g. scattered keywords across segments)
        val fallbackConvicts = fallbackVerdict?.isScam == true && fallbackVerdict.confidence >= 0.70f

        val finalIsScam = ensembleVerdict.isScam || weightedConvicts || fallbackConvicts
        val finalScamType = when {
            cloneResult.isCloned -> "ai_voice_kidnap"
            ensembleVerdict.isScam -> ensembleVerdict.scamType
            intentResult.isScam -> intentResult.dominantThreat ?: "unknown_scam"
            fallbackConvicts -> fallbackVerdict!!.category
            weightedConvicts -> "acoustic_anomaly"
            else -> null
        }
        val finalConfidence = when {
            cloneResult.isCloned -> maxOf(cloneResult.confidence, ensembleVerdict.confidence)
            ensembleVerdict.isScam -> ensembleVerdict.confidence
            fallbackConvicts -> fallbackVerdict!!.confidence
            weightedConvicts -> weightedRisk.coerceIn(0f, 0.99f)
            else -> maxOf(ensembleVerdict.confidence, 0f)
        }

        Log.i(
            TAG,
            "Verdict: ensemble=${ensembleVerdict.isScam}(conf=%.2f) clone=${cloneResult.isCloned}(conf=%.2f) intent=${intentResult.isScam} risk=%.2f => finalIsScam=$finalIsScam".format(
                ensembleVerdict.confidence, cloneResult.confidence, weightedRisk
            )
        )

        val flaggedSegments = (if (finalIsScam) ensembleVerdict.hits else emptyList()).map {
            FlaggedSegment(
                index = it.index,
                startSec = it.startSec,
                text = it.text,
                similarity = it.similarity,
                matchedCategory = it.matchedCategory ?: (finalScamType ?: "unknown")
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
            fullTranscript = fullTranscript,
            pipelineMs = pipelineMs
        )

        // Local threat intel + forensic evidence pack (scam only)
        if (detectionResult.isScam) {
            ThreatIntelClient.reportThreat(context, detectionResult)
            EvidenceManager.generateEvidencePackage(context, detectionResult)

            val topHitCategory = flaggedSegments.firstOrNull()?.matchedCategory
            if (topHitCategory != null && topHitCategory != "unknown") {
                FederatedLearningManager.logTruePositive(topHitCategory)
            }
        }

        return@coroutineScope detectionResult
    }

    private data class EngineBResult(
        val fullTranscript: String,
        val processedSegments: List<SegmentResult>,
        val segmentTranscripts: List<String>,
        val intentResult: IntentPrototypeClassifier.CallIntentResult,
        val maxLoudness: Float,
        val avgSimilarity: Float
    )

    private fun bootstrapEngines() {
        try {
            DeviceCapabilityManager.detectTier(context)
            FederatedLearningManager.attach(context)
            ScamPhraseLibrary.loadFromAssets(context)
            ScamEngineFallback.init(context)
            val embedPath = ModelDownloadManager.validatedEmbeddingModelPath(context)
            EmbeddingEngine.ensureInitialized(context, embedPath)
            intentClassifier.init()

            val deepfakePath = ModelDownloadManager.validatedDeepfakeModelPath(context)
            if (deepfakePath != null) {
                cloneDetector.init(deepfakePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Engine bootstrap incomplete — degraded mode active.", e)
        }
    }
}
