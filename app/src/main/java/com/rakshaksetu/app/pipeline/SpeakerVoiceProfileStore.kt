package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rakshaksetu.app.community.NumberNormalizer
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Cross-Session Speaker Consistency & Acoustic Profile Store
 *
 * Implements cross-session speaker verification:
 * 1. Computes 64-dimensional acoustic voiceprints from genuine call segments.
 * 2. Saves anonymous acoustic fingerprint profiles for trusted/family contacts locally.
 * 3. Compares ongoing incoming call voiceprints against the stored historical profile.
 * 4. Detects speaker identity anomalies when an attacker spoofs a trusted caller ID.
 *
 * DPDP Compliant: Zero audio retained; only compact 64-float statistical centroid vectors are stored.
 */
object SpeakerVoiceProfileStore {
    private const val TAG = "SpeakerProfileStore"
    private const val PROFILE_FILE_NAME = "speaker_voice_profiles.json"
    private const val EMBEDDING_DIM = 64
    private const val ANOMALY_SIMILARITY_THRESHOLD = 0.65f

    data class SpeakerProfile(
        val normalizedNumber: String,
        val contactName: String?,
        val acousticCentroid: List<Float>,
        val sampleCount: Int,
        val lastUpdatedEpochMs: Long = System.currentTimeMillis()
    )

    data class ConsistencyCheckResult(
        val hasProfile: Boolean,
        val similarity: Float,
        val isIdentityAnomaly: Boolean,
        val reason: String
    )

    /**
     * Extracts a 64-dimensional acoustic voiceprint from active voice PCM segments.
     * Uses mean & variance of Linear Frequency Cepstral Coefficients (LFCCs) + prosody metrics.
     */
    fun extractVoiceprint(pcmSegments: List<ByteArray>): FloatArray {
        if (pcmSegments.isEmpty()) return FloatArray(EMBEDDING_DIM)

        val totalPcm = pcmSegments.reduce { acc, bytes -> acc + bytes }
        val lfccFrames = SpectralFeatureExtractor.extractLFCC(totalPcm)
        val prosody = ProsodyAnalyzer.analyze(totalPcm)

        val voiceprint = FloatArray(EMBEDDING_DIM)
        val numFrames = lfccFrames.size / 60

        if (numFrames > 0) {
            // Mean across frames for 30 coefficients
            for (c in 0 until minOf(30, 60)) {
                var sum = 0.0f
                for (f in 0 until numFrames) {
                    sum += lfccFrames[f * 60 + c]
                }
                voiceprint[c] = sum / numFrames
            }

            // Variance across frames for 30 coefficients
            for (c in 0 until minOf(30, 60)) {
                val mean = voiceprint[c]
                var varSum = 0.0f
                for (f in 0 until numFrames) {
                    val diff = lfccFrames[f * 60 + c] - mean
                    varSum += diff * diff
                }
                voiceprint[30 + c] = sqrt(varSum / numFrames)
            }
        }

        // Slot 60-63: Prosody anchors (pitch, jitter, shimmer, hnr)
        voiceprint[60] = prosody.meanF0 / 300.0f
        voiceprint[61] = prosody.jitterLocal * 50.0f
        voiceprint[62] = prosody.shimmerLocal * 20.0f
        voiceprint[63] = (prosody.hnrDb + 20.0f) / 50.0f

        return normalizeVector(voiceprint)
    }

    /**
     * Compares an ongoing call's voiceprint against the saved historical profile for this number.
     */
    fun verifyConsistency(
        context: Context,
        rawNumber: String?,
        currentVoiceprint: FloatArray
    ): ConsistencyCheckResult {
        if (rawNumber.isNullOrBlank()) {
            return ConsistencyCheckResult(false, 1.0f, false, "Unknown caller number")
        }

        val normalized = NumberNormalizer.normalize(rawNumber)
        val profile = getProfile(context, normalized)
            ?: return ConsistencyCheckResult(false, 1.0f, false, "No historical baseline for this contact")

        val storedCentroid = profile.acousticCentroid.toFloatArray()
        val similarity = cosineSimilarity(currentVoiceprint, storedCentroid)
        val isAnomaly = similarity < ANOMALY_SIMILARITY_THRESHOLD

        val reason = if (isAnomaly) {
            "Voice signature mismatch (similarity=%.2f < threshold=%.2f) on contact %s"
                .format(similarity, ANOMALY_SIMILARITY_THRESHOLD, profile.contactName ?: normalized)
        } else {
            "Voice signature matches historical profile (similarity=%.2f)"
                .format(similarity)
        }

        Log.i(TAG, "Cross-Session Consistency [$normalized]: $reason")
        return ConsistencyCheckResult(
            hasProfile = true,
            similarity = similarity,
            isIdentityAnomaly = isAnomaly,
            reason = reason
        )
    }

    /**
     * Updates or enrolls a genuine call's acoustic profile for a trusted contact.
     */
    fun updateProfile(
        context: Context,
        rawNumber: String,
        contactName: String?,
        voiceprint: FloatArray
    ) {
        if (rawNumber.isBlank() || isZeroVector(voiceprint)) return

        val normalized = NumberNormalizer.normalize(rawNumber)
        val profiles = loadAllProfiles(context).toMutableMap()
        val existing = profiles[normalized]

        val updatedCentroid = if (existing != null) {
            // Running weighted average centroid update
            val n = existing.sampleCount
            val alpha = 1.0f / (n + 1).coerceAtMost(10)
            val merged = FloatArray(EMBEDDING_DIM)
            for (i in 0 until EMBEDDING_DIM) {
                merged[i] = (1.0f - alpha) * existing.acousticCentroid[i] + alpha * voiceprint[i]
            }
            normalizeVector(merged).toList()
        } else {
            voiceprint.toList()
        }

        val updated = SpeakerProfile(
            normalizedNumber = normalized,
            contactName = contactName ?: existing?.contactName,
            acousticCentroid = updatedCentroid,
            sampleCount = (existing?.sampleCount ?: 0) + 1
        )

        profiles[normalized] = updated
        saveProfiles(context, profiles)
        Log.i(TAG, "Updated acoustic voice profile for $normalized (samples=${updated.sampleCount})")
    }

    fun getProfile(context: Context, normalizedNumber: String): SpeakerProfile? {
        return loadAllProfiles(context)[normalizedNumber]
    }

    private fun loadAllProfiles(context: Context): Map<String, SpeakerProfile> {
        val file = File(context.filesDir, PROFILE_FILE_NAME)
        if (!file.exists()) return emptyMap()
        return try {
            val json = file.readText()
            val type = object : TypeToken<Map<String, SpeakerProfile>>() {}.type
            Gson().fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load speaker profiles", e)
            emptyMap()
        }
    }

    private fun saveProfiles(context: Context, profiles: Map<String, SpeakerProfile>) {
        try {
            val file = File(context.filesDir, PROFILE_FILE_NAME)
            val json = Gson().toJson(profiles)
            file.writeText(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save speaker profiles", e)
        }
    }

    private fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        var dot = 0.0f
        var norm1 = 0.0f
        var norm2 = 0.0f
        for (i in 0 until minOf(v1.size, v2.size)) {
            dot += v1[i] * v2[i]
            norm1 += v1[i] * v1[i]
            norm2 += v2[i] * v2[i]
        }
        val denom = sqrt(norm1) * sqrt(norm2)
        return if (denom > 1e-6f) (dot / denom).coerceIn(-1.0f, 1.0f) else 0.0f
    }

    private fun normalizeVector(v: FloatArray): FloatArray {
        var norm = 0.0f
        for (x in v) norm += x * x
        val mag = sqrt(norm)
        if (mag > 1e-6f) {
            for (i in v.indices) v[i] /= mag
        }
        return v
    }

    private fun isZeroVector(v: FloatArray): Boolean {
        return v.all { abs(it) < 1e-6f }
    }
}
