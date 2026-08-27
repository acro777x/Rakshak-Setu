package com.rakshaksetu.app.pipeline

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import java.io.File
import java.nio.FloatBuffer

/**
 * ENGINE A: Voice Clone / Deepfake Detector
 *
 * Detects AI-generated or cloned voices by analyzing spectral artifacts
 * invisible to human ears but present in synthetic speech:
 * - Phase discontinuities
 * - Harmonic artifacts
 * - Unnatural formant transitions
 *
 * Uses AASIST (Audio Anti-Spoofing using Integrated Spectro-Temporal
 * Graph Attention Networks) architecture. 85K parameters, ~340KB INT8.
 *
 * Runs in parallel with Engine B (ScamIntentDetector) via Kotlin coroutines.
 * Thread-safe: OrtSession supports concurrent inference.
 */
class CloneDetectorEngine(private val context: Context) {
    companion object {
        private const val TAG = "CloneDetector"
        private const val CLONE_THRESHOLD = 0.70f
        private const val SUSPECT_THRESHOLD = 0.50f
        private const val TARGET_AUDIO_LENGTH = 64600  // AASIST: ~4.04s at 16kHz
    }

    data class CloneResult(
        val isCloned: Boolean,
        val confidence: Float,
        val maxSegmentScore: Float,
        val avgSegmentScore: Float,
        val suspectSegmentIndices: List<Int>,
        val speechRate: Float,
        val silenceRatio: Float
    )

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var isInitialized = false
    private var modelMode: ModelMode = ModelMode.RAW_WAVEFORM

    enum class ModelMode {
        /** Model accepts raw waveform (RawNet-style) */
        RAW_WAVEFORM,
        /** Model accepts LFCC features (AASIST/LCNN-style) */
        LFCC_FEATURES
    }

    /**
     * Initialize with the ONNX deepfake detection model.
     * @param modelPath absolute path to the downloaded ONNX model
     * @param mode whether model expects raw waveform or LFCC features
     */
    @Synchronized
    fun init(modelPath: String, mode: ModelMode = ModelMode.RAW_WAVEFORM) {
        if (isInitialized) return
        val modelFile = File(modelPath)
        if (!modelFile.exists() || modelFile.length() < 1000) {
            Log.w(TAG, "Deepfake model not found at $modelPath")
            return
        }
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2) // limit threads for parallel execution
            }
            ortSession = ortEnv?.createSession(modelFile.absolutePath, opts)
            modelMode = mode
            isInitialized = true
            Log.i(TAG, "Engine A initialized: $modelPath (mode=$mode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load deepfake model", e)
        }
    }

    /**
     * Analyze multiple audio segments for voice cloning artifacts.
     * Called in parallel with Engine B via coroutineScope { async {} }.
     *
     * @param segments list of PCM audio segments (16-bit, 16kHz mono)
     * @return aggregated clone detection result
     */
    fun detectClone(segments: List<ByteArray>): CloneResult {
        if (segments.isEmpty()) {
            return CloneResult(false, 0f, 0f, 0f, emptyList(), 0f, 1f)
        }

        val segmentScores = mutableListOf<Float>()
        val suspectIndices = mutableListOf<Int>()

        // Acoustic, Prosody, and Neural Vocoder Artifact features from all segments combined
        val allPcm = segments.reduce { acc, bytes -> acc + bytes }
        val speechRate = SpectralFeatureExtractor.estimateSpeechRate(allPcm)
        val silenceRatio = SpectralFeatureExtractor.computeSilenceRatio(allPcm)
        val prosody = ProsodyAnalyzer.analyze(allPcm)
        val vocoderResult = NeuralVocoderDetector.analyzeVocoderArtifacts(allPcm)

        for ((idx, segment) in segments.withIndex()) {
            val score = analyzeSegment(segment)
            segmentScores.add(score)
            if (score > SUSPECT_THRESHOLD) {
                suspectIndices.add(idx)
            }
        }

        var maxScore = segmentScores.maxOrNull() ?: 0f
        var avgScore = if (segmentScores.isNotEmpty()) {
            segmentScores.sum() / segmentScores.size
        } else 0f

        // Prosody & Neural Vocoder fusion (WaveRNN / Subscale artifacts)
        val syntheticPrior = (prosody.ttsAnomalyScore * 0.55f + vocoderResult.neuralVocoderConfidence * 0.45f).coerceIn(0f, 1f)
        if (syntheticPrior > 0.45f) {
            maxScore = (maxScore + syntheticPrior * 0.22f).coerceAtMost(1.0f)
            avgScore = (avgScore + syntheticPrior * 0.18f).coerceAtMost(1.0f)
        }

        // Decision: require multiple suspicious segments for conviction
        // Single-segment spikes could be codec artifacts
        val isCloned = when {
            segments.size == 1 -> maxScore > CLONE_THRESHOLD + 0.10f || syntheticPrior > 0.82f
            segments.size <= 3 -> (suspectIndices.size >= 1 && avgScore > SUSPECT_THRESHOLD) || syntheticPrior > 0.78f
            else -> (suspectIndices.size >= 2 && avgScore > SUSPECT_THRESHOLD) || (syntheticPrior > 0.70f && maxScore > SUSPECT_THRESHOLD)
        }

        val confidence = if (isCloned) {
            (maxScore * 0.45f + avgScore * 0.25f + syntheticPrior * 0.30f).coerceIn(0f, 1f)
        } else {
            avgScore.coerceIn(0f, 1f)
        }

        Log.d(TAG, "Clone analysis: max=%.3f avg=%.3f suspects=%d/%d cloned=%s prosodyTTS=%.2f vocoderArt=%.2f"
            .format(maxScore, avgScore, suspectIndices.size, segments.size, isCloned, prosody.ttsAnomalyScore, vocoderResult.neuralVocoderConfidence))

        return CloneResult(
            isCloned = isCloned,
            confidence = confidence,
            maxSegmentScore = maxScore,
            avgSegmentScore = avgScore,
            suspectSegmentIndices = suspectIndices,
            speechRate = speechRate,
            silenceRatio = silenceRatio
        )
    }

    /**
     * Analyze a single audio segment for deepfake artifacts.
     * Returns probability of being AI-generated: 0.0 = real, 1.0 = cloned.
     */
    private fun analyzeSegment(pcmData: ByteArray): Float {
        if (!isInitialized || ortEnv == null || ortSession == null) {
            // Fallback: use acoustic heuristics when model unavailable
            return acousticHeuristicScore(pcmData)
        }

        return try {
            val inputFeatures = when (modelMode) {
                ModelMode.RAW_WAVEFORM -> {
                    SpectralFeatureExtractor.extractRawWaveform(pcmData, TARGET_AUDIO_LENGTH)
                }
                ModelMode.LFCC_FEATURES -> {
                    SpectralFeatureExtractor.extractLFCC(pcmData)
                }
            }

            // AASIST expects [batch, samples] for raw waveform
            val shape = when (modelMode) {
                ModelMode.RAW_WAVEFORM -> longArrayOf(1, TARGET_AUDIO_LENGTH.toLong())
                ModelMode.LFCC_FEATURES -> {
                    val numFrames = inputFeatures.size / 60
                    longArrayOf(1, numFrames.toLong(), 60)
                }
            }

            val inputBuffer = FloatBuffer.wrap(inputFeatures)
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, shape)

            val inputName = ortSession!!.inputNames.first()
            val result = ortSession!!.run(mapOf(inputName to inputTensor))

            // AASIST output: [1, 2] logits where [0]=bonafide, [1]=spoof
            val outputTensor = result[0].value
            val spoofScore = when (outputTensor) {
                is Array<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val logits = outputTensor as Array<FloatArray>
                    if (logits[0].size >= 2) {
                        // Softmax: P(spoof) = exp(logit_spoof) / (exp(logit_bonafide) + exp(logit_spoof))
                        val bonafideLogit = logits[0][0].toDouble()
                        val spoofLogit = logits[0][1].toDouble()
                        val expBonafide = Math.exp(bonafideLogit)
                        val expSpoof = Math.exp(spoofLogit)
                        val denom = expBonafide + expSpoof
                        if (denom > 0.0) (expSpoof / denom).toFloat() else 0.5f
                    } else {
                        logits[0][0]
                    }
                }
                is FloatArray -> outputTensor[0]
                else -> 0f
            }

            result.close()
            inputTensor.close()

            // Apply sigmoid only if single logit output (non-AASIST model)
            val probability = if (spoofScore < 0f || spoofScore > 1f) {
                (1.0f / (1.0f + Math.exp(-spoofScore.toDouble()))).toFloat()
            } else {
                spoofScore
            }

            probability
        } catch (e: Exception) {
            Log.e(TAG, "ONNX inference failed, using heuristic fallback", e)
            acousticHeuristicScore(pcmData)
        }
    }

    /**
     * Acoustic heuristic fallback when ONNX model unavailable.
     * Uses spectral features to estimate synthetic-ness.
     * NOT a replacement for a real model — just prevents null results.
     */
    private fun acousticHeuristicScore(pcmData: ByteArray): Float {
        val silenceRatio = SpectralFeatureExtractor.computeSilenceRatio(pcmData)
        val speechRate = SpectralFeatureExtractor.estimateSpeechRate(pcmData)

        // AI-generated speech tends to have very low silence ratio
        // and unnaturally consistent speech rate
        var suspicion = 0f

        if (silenceRatio < 0.05f) suspicion += 0.15f   // Almost no silence = suspicious
        if (speechRate > 5.0f) suspicion += 0.10f       // Unnaturally fast
        if (speechRate < 1.0f && silenceRatio < 0.3f) suspicion += 0.10f  // Steady drone

        return suspicion.coerceIn(0f, 0.40f) // Cap heuristic at 0.40 — can't convict alone
    }

    fun isReady(): Boolean = isInitialized

    fun release() {
        ortSession?.close()
        ortSession = null
        isInitialized = false
    }
}
