package com.rakshaksetu.voip.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Coordinates in-memory audio interception, real-time ASR, voice clone detection,
 * Wald's SPRT accumulator, and scam phrase trie matching.
 *
 * Emits reactive StateFlow for the Jetpack Compose HUD.
 */
class AiPipelineCoordinator(
    private val context: Context? = null,
    val ringBuffer: SpscAudioRingBuffer = SpscAudioRingBuffer()
) {
    companion object {
        private const val TAG = "AiPipelineCoordinator"
        const val WINDOW_BYTES = 96000 // 3.0s @ 16kHz 16-bit mono
        const val STRIDE_BYTES = 48000 // 1.5s stride
    }

    enum class ThreatLevel {
        SAFE,           // Emerald: Wald SPRT <= -4.600
        EVALUATING,     // Amber: -4.600 < Wald SPRT < 5.288
        CRITICAL_THREAT // Crimson: Wald SPRT >= 5.288 OR severe scam trigger
    }

    data class PipelineUiState(
        val threatLevel: ThreatLevel = ThreatLevel.SAFE,
        val sprtLambda: Float = 0.0f,
        val threatPercent: Float = 0.0f,
        val cloneProbability: Float = 0.0f,
        val scamRiskScore: Float = 0.0f,
        val detectedCategory: String = "No threat detected",
        val liveTranscript: String = "Listening to encrypted audio stream...",
        val matchedSpans: List<ScamPhraseTrie.MatchedSpan> = emptyList(),
        val audioRms: Float = 0.0f,
        val totalPcmBytesProcessed: Long = 0L,
        val isTamperSealed: Boolean = false,
        val isCallActive: Boolean = false
    )

    private val _uiState = MutableStateFlow(PipelineUiState())
    val uiState: StateFlow<PipelineUiState> = _uiState.asStateFlow()

    val cloneDetector = AasistCloneDetector(context)
    val sprtAccumulator = WaldSprtAccumulator()
    val scamTrie = ScamPhraseTrie().apply {
        if (context != null) loadFromAssets(context) else seedDefaults()
    }
    val asrEngine = StreamingAsrEngine(context)

    private val scope = CoroutineScope(Dispatchers.Default)
    private var processingJob: Job? = null

    init {
        asrEngine.setListener(object : StreamingAsrEngine.Listener {
            override fun onPartialResult(partialText: String) {
                handleTranscriptUpdate(partialText)
            }

            override fun onFinalResult(finalText: String) {
                handleTranscriptUpdate(asrEngine.getFullTranscript())
            }
        })
    }

    /**
     * Start pulling in-flight audio frames from SPSC ring buffer.
     */
    fun start() {
        if (processingJob?.isActive == true) return
        _uiState.update { it.copy(isCallActive = true) }

        processingJob = scope.launch {
            val readChunk = ByteArray(3200) // 100ms at 16kHz 16-bit PCM
            val slidingWindowBuffer = ByteArray(WINDOW_BYTES)
            var windowBytesFilled = 0
            var totalBytes = 0L

            while (isActive) {
                val available = ringBuffer.available()
                if (available >= readChunk.size) {
                    val bytesRead = ringBuffer.read(readChunk, 0, readChunk.size)
                    if (bytesRead > 0) {
                        totalBytes += bytesRead

                        // 1. Calculate instant RMS loudness for spectral waveform
                        val rms = calculateRms(readChunk, bytesRead)

                        // 2. Stream to ASR engine (<150ms latency)
                        asrEngine.acceptWaveForm(readChunk, bytesRead)

                        // 3. Accumulate sliding window for AASIST-L voice clone detector
                        var chunkOffset = 0
                        var ranAnalysis = false
                        while (chunkOffset < bytesRead) {
                            val copySize = minOf(bytesRead - chunkOffset, WINDOW_BYTES - windowBytesFilled)
                            System.arraycopy(readChunk, chunkOffset, slidingWindowBuffer, windowBytesFilled, copySize)
                            windowBytesFilled += copySize
                            chunkOffset += copySize

                            // When 3.0-second window is ready, run deepfake analysis
                            if (windowBytesFilled >= WINDOW_BYTES) {
                                val analysis = cloneDetector.analyzePcmWindow(slidingWindowBuffer, WINDOW_BYTES)
                                val sprtResult = sprtAccumulator.step(analysis.spoofScore)

                                updateThreatEvaluation(
                                    spoofScore = analysis.spoofScore,
                                    sprtResult = sprtResult,
                                    rms = rms,
                                    totalBytes = totalBytes
                                )
                                ranAnalysis = true

                                // Slide window by 1.5s stride (48,000 bytes)
                                System.arraycopy(
                                    slidingWindowBuffer,
                                    STRIDE_BYTES,
                                    slidingWindowBuffer,
                                    0,
                                    WINDOW_BYTES - STRIDE_BYTES
                                )
                                windowBytesFilled = WINDOW_BYTES - STRIDE_BYTES
                            }
                        }
                        if (!ranAnalysis) {
                            _uiState.update { it.copy(audioRms = rms, totalPcmBytesProcessed = totalBytes) }
                        }
                    }
                } else {
                    kotlinx.coroutines.delay(20) // Yield CPU
                }
            }
        }
    }

    /**
     * Updates UI state based on Wald's SPRT accumulator decision.
     */
    private fun updateThreatEvaluation(
        spoofScore: Float,
        sprtResult: WaldSprtAccumulator.SprtStepResult,
        rms: Float,
        totalBytes: Long
    ) {
        val threatLevel = when (sprtResult.decision) {
            WaldSprtAccumulator.Decision.THREAT_DETECTED -> ThreatLevel.CRITICAL_THREAT
            WaldSprtAccumulator.Decision.BONAFIDE_SAFE -> ThreatLevel.SAFE
            WaldSprtAccumulator.Decision.EVALUATING -> ThreatLevel.EVALUATING
        }

        val category = if (threatLevel == ThreatLevel.CRITICAL_THREAT) {
            "AI Synthetic Voice Clone Detected (AASIST-L + SPRT)"
        } else if (threatLevel == ThreatLevel.EVALUATING) {
            "Analyzing Acoustic Spectra..."
        } else {
            "Encrypted Sovereign Stream Verified Safe"
        }

        _uiState.update { current ->
            current.copy(
                threatLevel = threatLevel,
                sprtLambda = sprtResult.cumulativeScore,
                threatPercent = sprtAccumulator.getThreatConfidencePercent(),
                cloneProbability = spoofScore,
                detectedCategory = if (threatLevel != ThreatLevel.SAFE) category else current.detectedCategory,
                audioRms = rms,
                totalPcmBytesProcessed = totalBytes
            )
        }
    }

    /**
     * Processes live transcripts against Scam Phrase Trie.
     */
    private fun handleTranscriptUpdate(text: String) {
        val scan = scamTrie.scan(text)
        _uiState.update { current ->
            val threat = if (scan.isThreat || current.threatLevel == ThreatLevel.CRITICAL_THREAT) {
                ThreatLevel.CRITICAL_THREAT
            } else current.threatLevel

            val category = if (scan.isThreat) {
                "Scam Detected: ${scan.topCategory.replace("_", " ").uppercase()}"
            } else current.detectedCategory

            current.copy(
                threatLevel = threat,
                scamRiskScore = maxOf(scan.riskScore, current.scamRiskScore),
                detectedCategory = category,
                liveTranscript = text,
                matchedSpans = scan.matchedSpans
            )
        }
    }

    /**
     * Immediate Synthetic Voice Injection for testing: triggers Wald SPRT threshold within 3.0s
     * transitioning HUD from emerald safe to crimson threat.
     */
    fun simulateSyntheticVoiceInjection() {
        scope.launch {
            // Step 1: High spoof probability observation (0.92)
            val step1 = sprtAccumulator.step(0.92f)
            _uiState.update {
                it.copy(
                    threatLevel = ThreatLevel.EVALUATING,
                    sprtLambda = step1.cumulativeScore,
                    threatPercent = sprtAccumulator.getThreatConfidencePercent(),
                    cloneProbability = 0.92f,
                    detectedCategory = "Synthetic Pitch Perturbation Detected"
                )
            }
            kotlinx.coroutines.delay(1200)

            // Step 2: High spoof probability observation (0.96) -> Exceeds Wald A = 5.288
            val step2 = sprtAccumulator.step(0.96f)
            val fakeTranscript = "Main CBI cyber crime branch se bol raha hoon aapka digital arrest warrant jari hua hai"
            asrEngine.injectSimulatedTranscript(fakeTranscript)

            _uiState.update {
                it.copy(
                    threatLevel = ThreatLevel.CRITICAL_THREAT,
                    sprtLambda = step2.cumulativeScore,
                    threatPercent = 100.0f,
                    cloneProbability = 0.96f,
                    detectedCategory = "CRIMSON THREAT: AI Voice Clone + Digital Arrest Scam",
                    liveTranscript = fakeTranscript,
                    matchedSpans = scamTrie.scan(fakeTranscript).matchedSpans
                )
            }
        }
    }

    /**
     * Simulates clean conversational speech for testing zero false alarm stability.
     */
    fun simulateBonafideConversationalSpeech() {
        val step = sprtAccumulator.step(0.08f)
        val safeTranscript = "Namaste, kya aap kal subah project presentation meeting ke liye available hain?"
        asrEngine.injectSimulatedTranscript(safeTranscript)

        _uiState.update {
            it.copy(
                threatLevel = ThreatLevel.SAFE,
                sprtLambda = step.cumulativeScore,
                threatPercent = 0.0f,
                cloneProbability = 0.08f,
                scamRiskScore = 0.0f,
                detectedCategory = "Bonafide Human Voice Verified",
                liveTranscript = safeTranscript,
                matchedSpans = emptyList()
            )
        }
    }

    fun markTamperSealed() {
        _uiState.update { it.copy(isTamperSealed = true) }
    }

    fun stop() {
        processingJob?.cancel()
        processingJob = null
        _uiState.update { it.copy(isCallActive = false, audioRms = 0.0f) }
    }

    fun reset() {
        stop()
        ringBuffer.clear()
        sprtAccumulator.reset()
        asrEngine.clear()
        _uiState.value = PipelineUiState()
    }

    private fun calculateRms(pcmBytes: ByteArray, length: Int): Float {
        val numSamples = length / 2
        if (numSamples == 0) return 0.0f
        val byteBuffer = ByteBuffer.wrap(pcmBytes, 0, length).order(ByteOrder.LITTLE_ENDIAN)
        var sumSquares = 0.0
        for (i in 0 until numSamples) {
            val sample = byteBuffer.short / 32768.0
            sumSquares += sample * sample
        }
        val rms = sqrt(sumSquares / numSamples).toFloat()
        return (rms * 3.5f).coerceIn(0.0f, 1.0f)
    }
}
