package com.rakshaksetu.app.pipeline

import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object FederatedClient {
    private const val TAG = "FederatedClient"
    private const val FL_SERVER_URL = "http://10.0.2.2:5000/sync_weights"
    
    private val client = OkHttpClient()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun uploadLocalWeights(falsePositiveCount: Int, truePositiveCount: Int) {
        Log.i(TAG, "Initiating Federated Learning Weight Sync...")
        
        // Simulating weight deltas as a byte array hash for privacy
        val weightDeltas = "A3F8E9D2... (Encrypted Differential Weights)"
        
        val jsonObj = JSONObject().apply {
            put("client_id", "android-node-001")
            put("weight_deltas", weightDeltas)
            put("fp_count", falsePositiveCount)
            put("tp_count", truePositiveCount)
        }

        val requestBody = jsonObj.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(FL_SERVER_URL)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Federated Server Unreachable.", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.i(TAG, "Weights synced successfully. Server replied: \$responseBody")
                } else {
                    Log.e(TAG, "FL Server rejected sync: \${response.code}")
                }
                response.close()
            }
        })
    }
}
