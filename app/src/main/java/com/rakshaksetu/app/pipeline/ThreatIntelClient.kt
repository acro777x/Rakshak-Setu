package com.rakshaksetu.app.pipeline

import android.util.Log
import com.rakshaksetu.app.model.DetectionResult
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object ThreatIntelClient {
    private const val TAG = "ThreatIntelClient"
    // Threat Intel Server IP - Configurable endpoint
    private const val SERVER_URL = "http://10.0.2.2:5001/report_threat"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val UPI_PATTERN = Pattern.compile("[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}")

    fun reportThreat(result: DetectionResult) {
        if (!result.isScam) return
        
        Log.i(TAG, "Extracting Cyber Threat Intelligence from transcript...")
        
        val upis = extractUPIs(result.fullTranscript)
        
        if (upis.isEmpty()) {
            Log.d(TAG, "No specific UPI threat intelligence found in this call.")
            return
        }

        val jsonObj = JSONObject().apply {
            put("transcript", result.fullTranscript)
            put("extracted_upis", org.json.JSONArray(upis))
            put("confidence", result.confidence)
        }

        val requestBody = jsonObj.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(SERVER_URL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Threat Intelligence upload skipped or server offline: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i(TAG, "Threat Intelligence successfully logged at Central Server!")
                } else {
                    Log.w(TAG, "Threat Intel Server response: ${response.code}")
                }
                response.close()
            }
        })
    }

    private fun extractUPIs(text: String): List<String> {
        val matcher = UPI_PATTERN.matcher(text)
        val matches = mutableListOf<String>()
        while (matcher.find()) {
            matches.add(matcher.group())
        }
        return matches
    }
}
