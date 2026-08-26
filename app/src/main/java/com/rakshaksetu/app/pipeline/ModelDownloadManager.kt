package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Runtime model provisioning for the on-device AI stack.
 *
 * APK-size strategy: NO model bytes are bundled. The Vosk Kaldi ASR model (~40MB,
 * language-dependent) and the optional quantized MiniLM sentence encoder are fetched
 * on demand into app-private storage, keeping every ABI split well under 20MB.
 *
 * Download reliability (field-tested on Indian mobile networks):
 *  - HTTP Range RESUME: interrupted downloads continue from the last byte instead
 *    of restarting a 40MB transfer from zero.
 *  - Live throughput display so slow-but-progressing links look alive.
 *  - Generous timeouts tuned for high-latency international origins.
 *
 * Security hardening:
 *  - Zip-slip protection on extraction (canonical-path containment checks).
 *  - Structural validation of extracted models before they are marked ready.
 */
object ModelDownloadManager {

    private const val TAG = "ModelDownloadManager"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val PREFS_NAME = "rakshak_ai_models"
    private const val KEY_ASR_LANG = "key_asr_lang"
    private const val KEY_MINILM_DONE = "key_minilm_done"

    const val MODELS_ROOT_DIR = "ai_models"
    const val LANG_HINDI = "hi"
    const val LANG_ENGLISH = "en"

    private val ASR_SPECS = mapOf(
        LANG_HINDI to AsrSpec(
            langKey = LANG_HINDI,
            displayName = "Hindi + Hinglish",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            dirName = "vosk-model-small-hi-0.22"
        ),
        LANG_ENGLISH to AsrSpec(
            langKey = LANG_ENGLISH,
            displayName = "English (India/US)",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            dirName = "vosk-model-small-en-us-0.15"
        )
    )

    private const val MINILM_URL =
        "https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quantized.onnx"
    const val EMBEDDING_FILENAME = "MiniLM_quantized.onnx"

    private const val AASIST_LITE_URL =
        "https://huggingface.co/clovaai/aasist/resolve/main/aasist_lite_quantized.onnx"
    private const val AASIST_FULL_URL =
        "https://huggingface.co/clovaai/aasist/resolve/main/aasist_quantized.onnx"
    const val DEEPFAKE_FILENAME = "aasist_model.onnx"

    data class AsrSpec(
        val langKey: String,
        val displayName: String,
        val url: String,
        val dirName: String
    )

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Float, val fileName: String, val mbPerSec: Float = 0f) : DownloadState()
        data class Extracting(val fileName: String) : DownloadState()
        object Success : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Default language follows device locale, restricted to supported set. */
    fun selectedLanguage(context: Context): String {
        val stored = prefs(context).getString(KEY_ASR_LANG, null)
        if (stored != null && ASR_SPECS.containsKey(stored)) return stored
        val sysLang = Locale.getDefault().language.lowercase()
        return if (sysLang == LANG_HINDI) LANG_HINDI else LANG_ENGLISH
    }

    fun setSelectedLanguage(context: Context, langKey: String) {
        prefs(context).edit().putString(KEY_ASR_LANG, langKey).apply()
    }

    fun availableLanguages(): List<AsrSpec> = ASR_SPECS.values.toList()

    fun specFor(langKey: String): AsrSpec = ASR_SPECS[langKey] ?: ASR_SPECS.getValue(LANG_HINDI)

    fun modelsRoot(context: Context): File = File(context.filesDir, MODELS_ROOT_DIR)

    /** True when the selected-language Vosk model directory is structurally complete. */
    fun isAsrModelReady(context: Context): Boolean = validateAsrDir(asrModelDir(context)) != null

    fun asrModelDir(context: Context): File {
        val spec = specFor(selectedLanguage(context))
        return File(modelsRoot(context), spec.dirName)
    }

    /**
     * Returns the validated model directory path (consumed by org.vosk.Model),
     * or null when absent/incomplete.
     */
    fun validatedAsrModelPath(context: Context): String? =
        validateAsrDir(asrModelDir(context))?.absolutePath

    internal fun validateAsrDir(dir: File?): File? {
        if (dir == null || !dir.isDirectory) return null
        val confOk = File(dir, "conf").isDirectory ||
            File(dir, "conf.model").isFile ||
            File(dir, "am").isDirectory
        val graphOk = File(dir, "graph").isDirectory || File(dir, "graph.zip").isFile
        val amOk = File(dir, "am").isDirectory || File(dir, "final.mdl").isFile
        return if ((confOk || amOk) && graphOk && dir.listFiles()?.isNotEmpty() == true) dir else null
    }

    fun embeddingModelFile(context: Context): File = File(modelsRoot(context), EMBEDDING_FILENAME)

    fun isEmbeddingModelReady(context: Context): Boolean =
        embeddingModelFile(context).let { it.exists() && it.length() > 1_000_000 }

    fun validatedEmbeddingModelPath(context: Context): String? {
        val f = embeddingModelFile(context)
        return if (f.exists() && f.length() > 1_000_000) f.absolutePath else null
    }

    fun markEmbeddingReady(context: Context) {
        prefs(context).edit().putBoolean(KEY_MINILM_DONE, true).apply()
    }

    fun deepfakeModelFile(context: Context): File = File(modelsRoot(context), DEEPFAKE_FILENAME)

    fun isDeepfakeModelReady(context: Context): Boolean =
        deepfakeModelFile(context).let { it.exists() && it.length() > 100_000 }

    fun validatedDeepfakeModelPath(context: Context): String? {
        val f = deepfakeModelFile(context)
        return if (f.exists() && f.length() > 100_000) f.absolutePath else null
    }

    /** Downloads and extracts the Vosk model for the given language. */
    fun downloadAsrModel(context: Context, langKey: String): Flow<DownloadState> = flow {
        val spec = specFor(langKey)
        val root = modelsRoot(context)
        if (!root.exists()) root.mkdirs()

        val existing = File(root, spec.dirName)
        if (validateAsrDir(existing) != null) {
            emit(DownloadState.Success)
            return@flow
        }

        val tmpZip = File(root, "${spec.dirName}.zip.tmp")
        try {
            emit(DownloadState.Downloading(0f, spec.displayName))

            // Total size probe (also validates reachability early)
            var totalSize = -1L
            try {
                client.newCall(Request.Builder().url(spec.url).head().build()).execute().use { head ->
                    if (head.isSuccessful) {
                        val len = parseContentLength(head.header("Content-Length"))
                        if (len != null) totalSize = len
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "HEAD probe failed (${e.message}) — proceeding without size.")
            }

            // Resume support: continue from existing partial file when server allows ranges.
            var startFrom = 0L
            if (tmpZip.exists() && tmpZip.length() > 0 && totalSize > 0 && tmpZip.length() < totalSize) {
                startFrom = tmpZip.length()
                Log.i(TAG, "Resuming ${spec.displayName} from byte $startFrom / $totalSize")
            } else if (tmpZip.exists() && totalSize in 1..tmpZip.length()) {
                Log.i(TAG, "Stale partial matches/exceeds size — restarting.")
                tmpZip.delete()
            }

            val reqBuilder = Request.Builder().url(spec.url).get()
            if (startFrom > 0) reqBuilder.header("Range", "bytes=$startFrom-")
            client.newCall(reqBuilder.build()).execute().use { response ->
                val code = response.code
                val resumable = code == 206
                if (!(code == 200 || resumable)) {
                    val msg = "HTTP $code while downloading ${spec.displayName}"
                    Log.e(TAG, msg)
                    emit(DownloadState.Error(msg))
                    return@flow
                }
                if (startFrom > 0 && !resumable) {
                    Log.i(TAG, "Server ignored Range request — restarting from zero.")
                    startFrom = 0
                    tmpZip.delete()
                }

                val body = response.body ?: run {
                    emit(DownloadState.Error("Empty response for ${spec.displayName}"))
                    return@flow
                }
                if (totalSize <= 0) totalSize = parseContentLength(body.contentLength().toString()) ?: -1L

                emit(DownloadState.Extracting("").let { DownloadState.Downloading(if (totalSize > 0) startFrom.toFloat() / totalSize else 0f, spec.displayName) })

                val mode = if (startFrom > 0) FileOutputStream(tmpZip, true) else FileOutputStream(tmpZip, false)
                var writtenThisSession = 0L
                val sessionStartMs = System.currentTimeMillis()
                var lastEmit = 0f

                body.byteStream().use { input ->
                    mode.use { out ->
                        val buf = ByteArray(512 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            writtenThisSession += n

                            if (totalSize > 0) {
                                val done = startFrom + writtenThisSession
                                val p = done.toFloat() / totalSize
                                if (p - lastEmit >= 0.01f || p >= 1f) {
                                    lastEmit = p
                                    val elapsedSec =
                                        ((System.currentTimeMillis() - sessionStartMs) / 1000f).coerceAtLeast(0.5f)
                                    val mbps = (writtenThisSession / (1024f * 1024f)) / elapsedSec
                                    emit(DownloadState.Downloading(p, spec.displayName, mbps))
                                }
                            }
                        }
                    }
                }
            }

            emit(DownloadState.Extracting(spec.displayName))
            unzipSafely(tmpZip, root, spec.dirName)
            tmpZip.delete()

            if (validateAsrDir(existing) != null) {
                Log.i(TAG, "${spec.displayName} installed and validated.")
                emit(DownloadState.Success)
            } else {
                existing.deleteRecursively()
                val msg = "Extracted ${spec.displayName} failed structural validation"
                Log.e(TAG, msg)
                emit(DownloadState.Error(msg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "ASR model download failed (partial retained at ${tmpZip.name}: ${tmpZip.length()} bytes)", e)
            emit(DownloadState.Error("${e.javaClass.simpleName}: ${e.message ?: "download failed"} — retry continues from where it stopped"))
        }
    }.flowOn(Dispatchers.IO)

    /** Optional semantic-matching upgrade: quantized multilingual MiniLM encoder. */
    fun downloadEmbeddingModel(context: Context): Flow<DownloadState> = flow {
        val dest = embeddingModelFile(context)
        if (dest.exists() && dest.length() > 1_000_000) {
            emit(DownloadState.Success)
            return@flow
        }
        val tmp = File(modelsRoot(context), "$EMBEDDING_FILENAME.tmp")
        try {
            emit(DownloadState.Downloading(0f, EMBEDDING_FILENAME))
            downloadWithResume(tmp, MINILM_URL)
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
            if (dest.length() > 1_000_000) {
                markEmbeddingReady(context)
                emit(DownloadState.Success)
            } else {
                dest.delete()
                emit(DownloadState.Error("MiniLM download incomplete"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "MiniLM download failed (partial at ${tmp.name}: ${tmp.length()} bytes)", e)
            emit(DownloadState.Error("${e.javaClass.simpleName} — will resume next attempt"))
        }
    }.flowOn(Dispatchers.IO)

    /** Voice clone / deepfake detection model provision. */
    fun downloadDeepfakeModel(context: Context): Flow<DownloadState> = flow {
        val dest = deepfakeModelFile(context)
        if (dest.exists() && dest.length() > 100_000) {
            emit(DownloadState.Success)
            return@flow
        }
        val tmp = File(modelsRoot(context), "$DEEPFAKE_FILENAME.tmp")
        val tier = DeviceCapabilityManager.detectTier(context)
        val url = if (tier == DeviceCapabilityManager.AiTier.FULL) AASIST_FULL_URL else AASIST_LITE_URL
        val modelLabel = if (tier == DeviceCapabilityManager.AiTier.FULL) "AASIST (Full)" else "AASIST-Lite"

        try {
            emit(DownloadState.Downloading(0f, modelLabel))
            downloadWithResume(tmp, url)
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
            if (dest.length() > 100_000) {
                emit(DownloadState.Success)
            } else {
                dest.delete()
                emit(DownloadState.Error("Deepfake model download incomplete"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Deepfake model download failed: ${e.message}", e)
            emit(DownloadState.Error("Voice clone model: ${e.message ?: "download failed"}"))
        }
    }.flowOn(Dispatchers.IO)

    /** Range-resumable single-file downloader with progress logging. */
    private fun downloadWithResume(dest: File, url: String) {
        var total = -1L
        runCatching {
            client.newCall(Request.Builder().url(url).head().build()).execute().use {
                if (it.isSuccessful) total = parseContentLength(it.header("Content-Length")) ?: -1L
            }
        }

        var startFrom = if (dest.exists() && total > 0 && dest.length() < total) dest.length() else 0L
        if (startFrom == 0L) dest.delete()

        val reqBuilder = Request.Builder().url(url).get()
        if (startFrom > 0) reqBuilder.header("Range", "bytes=$startFrom-")

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (response.code != 200 && response.code != 206) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            if (startFrom > 0 && response.code != 206) {
                startFrom = 0
                dest.delete()
            }
            val body = response.body ?: throw IllegalStateException("empty body")
            val out = FileOutputStream(dest, startFrom > 0)
            var sessionBytes = 0L
            body.byteStream().use { input ->
                out.use { o ->
                    val buf = ByteArray(512 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        o.write(buf, 0, n)
                        sessionBytes += n
                    }
                }
            }
            Log.i(TAG, "downloadWithResume wrote $sessionBytes bytes this session ($url)")
        }
    }

    private fun parseContentLength(raw: String?): Long? =
        raw?.toLongOrNull()?.takeIf { it > 0 }

    /**
     * Extracts a ZIP archive into [targetRoot]/[expectedTopDir] with strict zip-slip
     * protection: every entry's canonical path must remain inside the destination.
     */
    internal fun unzipSafely(zipFile: File, targetRoot: File, expectedTopDir: String) {
        val destDir = File(targetRoot, expectedTopDir)
        if (!destDir.exists()) destDir.mkdirs()
        val destCanonical = destDir.canonicalPath + File.separator

        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetRoot, entry.name)
                val outCanonical = outFile.canonicalPath
                if (!(outCanonical + File.separator).startsWith(destCanonical) &&
                    outCanonical != destDir.canonicalPath
                ) {
                    throw SecurityException("Blocked zip-slip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
                    FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
