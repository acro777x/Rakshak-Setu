package com.rakshaksetu.app.feedback

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.rakshaksetu.app.security.CallIdValidator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

data class FeedbackEntry(val callId: String, val timestamp: Long, val feedback: String, val reason: String?)

class FeedbackLogger(private val context: Context) {
    private val gson = Gson()
    private val lock = Any()
    
    private fun getFeedbackDir(): File {
        val dir = File(context.filesDir, "feedback")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun logNotScam(callId: String, reason: String? = null) {
        try {
            val validId = CallIdValidator.requireValid(callId)
            val entry = FeedbackEntry(validId, System.currentTimeMillis(), "not_scam", reason)
            saveEntry(entry)
        } catch (e: Exception) {
            Log.e("FeedbackLogger", "Failed to log not scam", e)
        }
    }

    fun logConfirmedScam(callId: String) {
        try {
            val validId = CallIdValidator.requireValid(callId)
            val entry = FeedbackEntry(validId, System.currentTimeMillis(), "confirmed_scam", null)
            saveEntry(entry)
        } catch (e: Exception) {
            Log.e("FeedbackLogger", "Failed to log confirmed scam", e)
        }
    }

    private fun saveEntry(entry: FeedbackEntry) {
        synchronized(lock) {
            try {
                val file = File(getFeedbackDir(), "${entry.callId}.json")
                FileOutputStream(file).use { fos ->
                    OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                        gson.toJson(entry, writer)
                    }
                }
            } catch (e: Exception) {
                Log.e("FeedbackLogger", "Error saving entry", e)
            }
        }
    }

    fun getFeedback(callId: String): FeedbackEntry? {
        synchronized(lock) {
            return try {
                val validId = CallIdValidator.requireValid(callId)
                val file = File(getFeedbackDir(), "$validId.json")
                if (!file.exists()) return null
                FileInputStream(file).use { fis ->
                    InputStreamReader(fis, Charsets.UTF_8).use { reader ->
                        gson.fromJson(reader, FeedbackEntry::class.java)
                    }
                }
            } catch (e: Exception) {
                Log.e("FeedbackLogger", "Error getting feedback", e)
                null
            }
        }
    }
}
