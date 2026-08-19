package com.rakshaksetu.app.pipeline

import android.util.Log
import com.rakshaksetu.app.model.DetectionResult

/**
 * AI-P4-01: Threat Intelligence Client
 * Extracts potential UPI IDs/URLs locally and sends them to the Threat Intel Server 
 * ONLY IF user consents or it's flagged as a high-risk scam campaign.
 */
object ThreatIntelClient {
    private const val TAG = "ThreatIntelClient"
    private const val SERVER_URL = "http://localhost:5001/report_scam"

    fun reportThreat(result: DetectionResult) {
        if (!result.isScam) return
        
        Log.i(TAG, "Extracting Threat Intelligence (NER) from transcript...")
        
        // Simple Regex to extract UPIs on-device as a pre-filter before sending to server
        val upiRegex = "[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}".toRegex()
        val foundUpis = upiRegex.findAll(result.fullTranscript).map { it.value }.toList()
        
        if (foundUpis.isNotEmpty()) {
            Log.w(TAG, "🚨 Potential Fraudulent UPIs detected: $foundUpis")
        }

        // Mock upload to python Threat Intel Server for Graph Clustering
        Log.i(TAG, "Uploading anonymized threat vector to Central Graph Server...")
    }
}
