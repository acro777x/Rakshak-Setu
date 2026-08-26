package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.rakshaksetu.app.model.DetectionResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Cyber Threat Intelligence extractor.
 *
 * DPDP-first design: extracted indicators (UPI handles) are stored locally under
 * app-private storage and surfaced to the user for voluntary reporting through the
 * NCRP/Chakshu assistant or community blacklist upload. No silent cloud egress.
 */
object ThreatIntelClient {
    private const val TAG = "ThreatIntelClient"
    private const val INTEL_DIR = "intel"

    private val UPI_PATTERN = Pattern.compile("[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}")
    private const val MAX_STORED_IOCS = 500

    data class ThreatIoc(
        val upiId: String,
        val callId: String,
        val phoneNumber: String,
        val scamType: String?,
        val confidence: Float,
        val capturedEpochMs: Long = System.currentTimeMillis()
    )

    fun reportThreat(context: Context?, result: DetectionResult) {
        if (!result.isScam || context == null) return

        try {
            val upis = extractUPIs(result.fullTranscript)
            if (upis.isEmpty()) {
                Log.d(TAG, "No UPI threat intelligence found in this call.")
                return
            }

            val dir = File(context.filesDir, INTEL_DIR)
            if (!dir.exists()) dir.mkdirs()

            val storeFile = File(dir, "upi_iocs.json")
            val existing: MutableList<ThreatIoc> = loadExisting(storeFile)

            upis.forEach { upi ->
                if (existing.none { it.upiId.equals(upi, ignoreCase = true) }) {
                    existing.add(
                        ThreatIoc(
                            upiId = upi.lowercase(Locale.ROOT),
                            callId = result.callId,
                            phoneNumber = result.phoneNumber,
                            scamType = result.scamType,
                            confidence = result.confidence
                        )
                    )
                    Log.i(TAG, "Stored local UPI IOC: $upi")
                }
            }

            while (existing.size > MAX_STORED_IOCS) existing.removeAt(0)

            storeFile.writeText(Gson().toJson(existing))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist threat IOCs", e)
        }
    }

    private fun loadExisting(storeFile: File): MutableList<ThreatIoc> =
        try {
            if (storeFile.exists()) {
                val type = com.google.gson.reflect.TypeToken.getParameterized(
                    List::class.java, ThreatIoc::class.java
                ).type
                Gson().fromJson<List<ThreatIoc>>(storeFile.readText(), type)
                    ?.toMutableList() ?: mutableListOf()
            } else mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }

    /** Human-readable digest embedded into the NCRP dossier "Annexure". */
    fun buildIocDigestForUser(context: Context): String {
        val storeFile = File(File(context.filesDir, INTEL_DIR), "upi_iocs.json")
        val iocs = loadExisting(storeFile)
        if (iocs.isEmpty()) return ""
        val df = SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.US)
        return iocs.joinToString("\n") { ioc ->
            "- UPI handle ${ioc.upiId} captured on ${df.format(Date(ioc.capturedEpochMs))} (call ref ${ioc.callId.take(8)})" +
                " — verify at chakshu.gov.in and report via NCRP."
        }
    }

    internal fun extractUPIs(text: String): List<String> {
        val matcher = UPI_PATTERN.matcher(text)
        val matches = mutableListOf<String>()
        while (matcher.find()) matches.add(matcher.group())
        return matches.distinct()
    }
}
