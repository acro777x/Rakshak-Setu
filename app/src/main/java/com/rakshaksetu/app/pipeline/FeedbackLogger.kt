package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.rakshaksetu.app.model.DetectionResult
import java.io.File
import java.io.FileWriter

data class FeedbackLog(
    val callId: String,
    val isScamActual: Boolean,
    val isScamPredicted: Boolean,
    val fullTranscript: String,
    val timestampMs: Long = System.currentTimeMillis()
)

object FeedbackLogger {
    private const val TAG = "FeedbackLogger"
    private const val FEEDBACK_FILE_NAME = "ai_feedback_loop.jsonl"

    /**
     * AI-P1-05: Feedback Evaluation Loop
     * Logs user feedback (e.g., False Positive) so it can be used for retraining the models later.
     */
    fun logFeedback(context: Context, result: DetectionResult, userSaysIsScam: Boolean) {
        try {
            val logFile = File(context.filesDir, FEEDBACK_FILE_NAME)
            
            val logEntry = FeedbackLog(
                callId = result.callId,
                isScamActual = userSaysIsScam,
                isScamPredicted = result.isScam,
                fullTranscript = result.fullTranscript
            )

            val jsonStr = Gson().toJson(logEntry)
            
            FileWriter(logFile, true).use { writer ->
                writer.append(jsonStr)
                writer.append("\n")
            }

            Log.i(TAG, "Feedback logged successfully for call ${result.callId}. Actual=$userSaysIsScam, Predicted=${result.isScam}")

        } catch (e: Exception) {
            Log.e(TAG, "Error logging feedback for evaluation loop", e)
        }
    }
}
