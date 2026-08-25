package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/** Contract for on-device speech-to-text over 16kHz mono 16-bit PCM. */
interface AsrEngine {
    suspend fun transcribe(pcmData: ByteArray): String
}

/**
 * Deterministic energy/ZCR gate used when no ASR model is available.
 * Returns empty text (never fabricated content) so downstream semantic
 * matching simply abstains instead of producing false positives.
 */
object AcousticFallbackTranscriber {

    private const val RMS_SPEECH_THRESHOLD = 300.0

    fun isSpeech(pcmData: ByteArray): Boolean {
        if (pcmData.size < 320) return false
        var sumSquare = 0.0
        for (i in 0 until pcmData.size - 1 step 2) {
            val sample = ((pcmData[i].toInt() and 0xFF) or (pcmData[i + 1].toInt() shl 8)).let {
                if (it > 32767) it - 65536 else it
            }
            sumSquare += (sample * sample).toDouble()
        }
        val numSamples = pcmData.size / 2
        val rms = kotlin.math.sqrt(sumSquare / numSamples)
        return rms >= RMS_SPEECH_THRESHOLD
    }
}

/**
 * Process-wide singleton holder for the Vosk Kaldi model.
 * Model load takes seconds and ~150MB RAM; loaded once lazily off the main thread.
 */
object VoskModelHolder {
    private const val TAG = "VoskModelHolder"

    @Volatile
    private var cachedModel: Model? = null
    private val mutex = Mutex()

    suspend fun getOrCreate(context: Context): Model? = withContext(Dispatchers.IO) {
        cachedModel?.let { return@withContext it }
        mutex.withLock {
            cachedModel?.let { return@withLock it }
            val path = ModelDownloadManager.validatedAsrModelPath(context.applicationContext)
            if (path == null) {
                Log.i(TAG, "Vosk model not downloaded yet — ASR will use acoustic gate only.")
                return@withLock null
            }
            try {
                org.vosk.LibVosk.setLogLevel(org.vosk.LogLevel.WARNINGS)
                val model = Model(path)
                Log.i(TAG, "Vosk model loaded from $path")
                cachedModel = model
                model
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to load Vosk model at $path", e)
                null
            }
        }
    }

    fun release() {
        try {
            cachedModel?.close()
        } catch (ignored: Throwable) {
        }
        cachedModel = null
    }
}

/**
 * True offline ASR using Vosk (Kaldi). Streams 5s PCM segments through a single-shot
 * Recognizer; falls back to the deterministic acoustic gate when no model exists yet.
 */
class VoskAsrEngine(private val context: Context) : AsrEngine {

    companion object {
        private const val TAG = "VoskAsrEngine"
        private const val SAMPLE_RATE = 16000f
        private const val FEED_CHUNK_BYTES = 16_000 // 0.5s of 16kHz mono 16-bit
    }

    override suspend fun transcribe(pcmData: ByteArray): String {
        if (pcmData.isEmpty()) return ""

        if (!AcousticFallbackTranscriber.isSpeech(pcmData)) return ""

        val model = VoskModelHolder.getOrCreate(context) ?: run {
            Log.d(TAG, "ASR model unavailable — acoustic gate passed, transcript unavailable.")
            return ""
        }

        return withContext(Dispatchers.Default) {
            val recognizer = try {
                Recognizer(model, SAMPLE_RATE)
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to create Vosk Recognizer", e)
                return@withContext ""
            }
            try {
                recognizer.acceptWaveForm(pcmData, pcmData.size)
                val text = extractText(recognizer.finalResult)
                Log.d(TAG, "Vosk transcribed ${pcmData.size} bytes: '$text'")
                text
            } catch (e: Throwable) {
                Log.e(TAG, "Vosk recognition failed — returning empty transcript", e)
                ""
            } finally {
                try {
                    recognizer.close()
                } catch (ignored: Throwable) {
                }
            }
        }
    }

    internal fun extractText(voskJson: String): String =
        try {
            JSONObject(voskJson).optString("text", "").trim()
        } catch (e: Exception) {
            ""
        }
}
