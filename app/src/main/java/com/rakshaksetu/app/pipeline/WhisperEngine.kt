package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume

/**
 * True AI Backend Whisper Engine
 * Sends PCM data to the local Python AI Backend (Whisper) to get real transcription.
 */
class WhisperEngine(private val context: Context) {

    companion object {
        private const val TAG = "WhisperBackendEngine"
        private const val WHISPER_SERVER_URL = "http://10.0.2.2:5000/transcribe"
    }

    private val client = OkHttpClient()

    init {
        Log.i(TAG, "Initialized Whisper Engine (Connecting to AI Backend: \$WHISPER_SERVER_URL)")
    }

    /**
     * Uploads the PCM byte array to the AI backend and awaits transcription.
     */
    suspend fun transcribe(pcmData: ByteArray): String {
        return suspendCancellableCoroutine { continuation ->
            Log.d(TAG, "Uploading \${pcmData.size} bytes to AI Backend for transcription...")

            // Send raw PCM as application/octet-stream
            val requestBody = pcmData.toRequestBody("application/octet-stream".toMediaType())
            
            val request = Request.Builder()
                .url(WHISPER_SERVER_URL)
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e(TAG, "Failed to reach AI Backend Whisper server.", e)
                    if (continuation.isActive) continuation.resume("Error: AI Backend Unreachable.")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string() ?: "{}"
                            val json = JSONObject(responseBody)
                            val text = json.optString("text", "No speech detected.")
                            Log.i(TAG, "AI Backend transcribed: \$text")
                            if (continuation.isActive) continuation.resume(text)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing JSON from AI Backend", e)
                            if (continuation.isActive) continuation.resume("Error: Invalid AI Backend Response")
                        }
                    } else {
                        Log.e(TAG, "AI Backend rejected transcription: \${response.code}")
                        if (continuation.isActive) continuation.resume("Error: Server \${response.code}")
                    }
                    response.close()
                }
            })
        }
    }

    fun release() {
        // No resources to release for HTTP client
    }
}
