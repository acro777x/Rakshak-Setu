package com.rakshaksetu.app.pipeline

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.regex.Pattern

object ThreatIntelClient {
    private const val TAG = "ThreatIntelClient"
    // Threat Intel Server IP - Assuming local network or production IP
    private const val SERVER_URL = "http://10.0.2.2:5001/report_threat"
    
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val UPI_PATTERN = Pattern.compile("[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}")

    fun reportThreat(result: DetectionResult) {
        if (!result.isScam) return
        
        Log.i(TAG, "Extracting Cyber Threat Intelligence from transcript...")
        
        val upis = extractUPIs(result.text)
        
        if (upis.isEmpty()) {
            Log.d(TAG, "No specific UPI threat intelligence found in this call.")
            return
        }

        val jsonObj = JSONObject().apply {
            put("transcript", result.text)
            put("extracted_upis", org.json.JSONArray(upis))
            put("confidence", result.confidenceScore)
        }

        val requestBody = jsonObj.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(SERVER_URL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to upload Threat Intelligence to Cyber Cell Server", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i(TAG, "Threat Intelligence successfully logged at Central Server!")
                } else {
                    Log.e(TAG, "Threat Intel Server rejected payload: \${response.code}")
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
