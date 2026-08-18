package com.rakshaksetu.app.pipeline

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

object ModelDownloadManager {

    private val client = OkHttpClient()

    // Example HuggingFace URLs (replace with Firebase Storage/CDN in production)
    private const val WHISPER_MODEL_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin"
    private const val EMBEDDING_MODEL_URL = "https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model_quantized.onnx"

    const val WHISPER_FILENAME = "ggml-tiny.bin"
    const val EMBEDDING_FILENAME = "MiniLM_quantized.onnx"

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Float, val fileName: String) : DownloadState()
        object Success : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    /**
     * Checks if both required model files exist in internal storage.
     */
    fun areModelsReady(context: Context): Boolean {
        val whisperFile = File(context.filesDir, WHISPER_FILENAME)
        val embeddingFile = File(context.filesDir, EMBEDDING_FILENAME)
        return whisperFile.exists() && embeddingFile.exists()
    }

    /**
     * Downloads a file and emits progress updates.
     */
    private fun downloadFile(context: Context, url: String, fileName: String): Flow<DownloadState> = flow {
        val file = File(context.filesDir, fileName)
        
        // If file already exists and is not empty, skip download (assuming valid for now)
        if (file.exists() && file.length() > 0) {
            emit(DownloadState.Success)
            return@flow
        }

        try {
            emit(DownloadState.Downloading(0f, fileName))
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit(DownloadState.Error("Failed to download $fileName: HTTP ${response.code}"))
                return@flow
            }
            
            val body = response.body
            if (body == null) {
                emit(DownloadState.Error("Empty response body for $fileName"))
                return@flow
            }

            val contentLength = body.contentLength()
            var bytesReadTotal = 0L
            val buffer = ByteArray(8 * 1024)
            
            val inputStream: InputStream = body.byteStream()
            val outputStream = FileOutputStream(file)
            
            var lastProgress = 0f
            
            inputStream.use { input ->
                outputStream.use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesReadTotal += bytesRead
                        
                        if (contentLength > 0) {
                            val currentProgress = (bytesReadTotal.toFloat() / contentLength)
                            // Only emit if progress increased by 1% to avoid flooding
                            if (currentProgress - lastProgress >= 0.01f || currentProgress == 1.0f) {
                                lastProgress = currentProgress
                                emit(DownloadState.Downloading(currentProgress, fileName))
                            }
                        }
                    }
                }
            }
            emit(DownloadState.Success)
        } catch (e: Exception) {
            // Clean up partial file
            if (file.exists()) file.delete()
            emit(DownloadState.Error(e.message ?: "Unknown download error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Downloads both models sequentially.
     */
    fun downloadAllModels(context: Context): Flow<DownloadState> = flow {
        // Download Whisper
        var whisperSuccess = false
        downloadFile(context, WHISPER_MODEL_URL, WHISPER_FILENAME).collect { state ->
            if (state is DownloadState.Error) {
                emit(state)
                return@collect
            }
            if (state is DownloadState.Success) {
                whisperSuccess = true
            } else {
                emit(state) // pass through downloading state
            }
        }

        if (!whisperSuccess) return@flow

        // Download Embedding Model
        downloadFile(context, EMBEDDING_MODEL_URL, EMBEDDING_FILENAME).collect { state ->
            emit(state) // Pass through all states for the second model
        }
    }
}
