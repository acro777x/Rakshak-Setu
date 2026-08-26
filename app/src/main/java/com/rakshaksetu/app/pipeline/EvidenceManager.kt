package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.rakshaksetu.app.model.DetectionResult
import java.io.File
import java.security.MessageDigest

/**
 * AI-P4-02: Forensic Evidence Packager — v2 "chain-of-custody" edition.
 *
 * Field-hardened improvements over v1:
 *  - SOURCE AUDIO PRESERVATION: OEM gallery cleaners purge recordings aggressively;
 *    for scam verdicts the source recording is copied into app-private evidence
 *    storage immediately and SHA-256 hashed at capture time.
 *  - TAMPER-EVIDENT MANIFEST: transcript hash, copied-audio hash/size, pipeline
 *    timings (fetch/decode/asr/embed/vote), flagged segments and the two-tier
 *    verdict inputs are frozen into manifest.json at analysis time.
 *  - EXPORTABLE DOSSIER: [buildDossierExport] renders a single human-readable
 *    document (statement + technical annexure) that the user shares to NCRP,
 *    bank or police via the standard Android share sheet.
 *
 * DPDP: everything stays in app-private storage until the user explicitly shares.
 */
object EvidenceManager {
    private const val TAG = "EvidenceManager"

    data class EvidenceManifest(
        val callId: String,
        val phoneNumberMasked: String,
        val callEndEpochMs: Long,
        val durationSec: Int,
        val isScam: Boolean,
        val confidence: Float,
        val scamType: String?,
        val originalAudioUri: String,
        val preservedAudioFile: String?,
        val preservedAudioBytes: Long?,
        val preservedAudioSha256: String?,
        val transcriptSha256: String,
        val fullTranscriptChars: Int,
        val flaggedSegments: List<FlaggedEntry>,
        val pipelineTimingsMs: TimingEntry,
        val generatedEpochMs: Long = System.currentTimeMillis()
    )

    data class FlaggedEntry(
        val index: Int,
        val startSec: Int,
        val text: String,
        val similarity: Float,
        val matchedCategory: String
    )

    data class TimingEntry(
        val fetch: Long,
        val decode: Long,
        val asr: Long,
        val embed: Long,
        val vote: Long
    )

    fun generateEvidencePackage(context: Context, result: DetectionResult) {
        if (!result.isScam) return

        try {
            val dir = evidenceDir(context, result.callId)
            if (!dir.exists()) dir.mkdirs()

            // 1. Preserve the source recording before the OS can purge it.
            val preserved = preserveSourceAudio(context, result)

            // 2. Freeze the tamper-evident manifest.
            val manifest = EvidenceManifest(
                callId = result.callId,
                phoneNumberMasked = maskNumber(result.phoneNumber),
                callEndEpochMs =
                    if (result.callEndEpoch > 100_000_000_000L) result.callEndEpoch
                    else result.callEndEpoch * 1000L,
                durationSec = result.durationSec,
                isScam = result.isScam,
                confidence = result.confidence,
                scamType = result.scamType,
                originalAudioUri = result.audioUri,
                preservedAudioFile = preserved?.second?.name,
                preservedAudioBytes = preserved?.second?.length(),
                preservedAudioSha256 = preserved?.let { sha256File(it.second) },
                transcriptSha256 = sha256String(result.fullTranscript),
                fullTranscriptChars = result.fullTranscript.length,
                flaggedSegments = result.flaggedSegments.map {
                    FlaggedEntry(it.index, it.startSec, it.text, it.similarity, it.matchedCategory)
                },
                pipelineTimingsMs = TimingEntry(
                    fetch = result.pipelineMs.fetch,
                    decode = result.pipelineMs.decode,
                    asr = result.pipelineMs.asr,
                    embed = result.pipelineMs.embed,
                    vote = result.pipelineMs.vote
                )
            )
            File(dir, "manifest.json").writeText(Gson().toJson(manifest))
            Log.i(TAG, "Evidence manifest secured. Transcript SHA-256: ${manifest.transcriptSha256.take(16)}…")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate evidence package", e)
        }
    }

    /**
     * Copies the recording through its content URI into private evidence storage.
     * Best-effort: returns null when the source vanished (already purged).
     */
    internal fun preserveSourceAudio(context: Context, result: DetectionResult): Pair<String, File>? {
        return try {
            val uri = android.net.Uri.parse(result.audioUri)
            if (uri.scheme != "content") return null

            val ext = guessExtension(context, uri)
            val dest = File(evidenceDir(context, result.callId), "source_audio$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null

            if (dest.length() < 44) { dest.delete(); return null }
            Log.i(TAG, "Source recording preserved (${dest.length()} bytes).")
            Pair(uri.toString(), dest)
        } catch (e: Exception) {
            Log.d(TAG, "Audio preservation skipped (${e.javaClass.simpleName}).")
            null
        }
    }

    /**
     * Renders the complete human-readable dossier for sharing with NCRP / bank / police.
     */
    fun buildDossierExport(context: Context, result: DetectionResult): String {
        val statement = try {
            StatementGeneratorBridge.statement(context, result)
        } catch (e: Exception) {
            ""
        }

        val manifestText = buildString {
            appendLine("=== RAKSHAK SETU — FORENSIC EVIDENCE DOSSIER ===")
            appendLine("Call reference : ${result.callId}")
            appendLine("Caller         : ${maskNumber(result.phoneNumber)}")
            appendLine("Verdict        : ${if (result.isScam) "SCAM" else "BENIGN"} @ ${(result.confidence * 100).toInt()}% confidence")
            appendLine("Category       : ${result.scamType?.replace('_', ' ')?.uppercase() ?: "n/a"}")
            appendLine("Duration       : ${result.durationSec}s")
            appendLine()
            if (statement.isNotBlank()) {
                appendLine("--- COMPLAINT STATEMENT ---")
                appendLine(statement)
                appendLine()
            }
            if (result.fullTranscript.isNotBlank()) {
                appendLine("--- ON-DEVICE TRANSCRIPT (ANNEXURE A) ---")
                appendLine(result.fullTranscript)
                appendLine("Transcript SHA-256: ${sha256String(result.fullTranscript)}")
                appendLine()
            }
            if (result.flaggedSegments.isNotEmpty()) {
                appendLine("--- FLAGGED SCRIPT SEGMENTS (ANNEXURE B) ---")
                result.flaggedSegments.forEach {
                    appendLine("[t+${it.startSec}s] (${(it.similarity * 100).toInt()}% match, ${it.matchedCategory}) \"${it.text}\"")
                }
                appendLine()
            }
            appendLine("--- TECHNICAL PIPELINE TIMINGS (ANNEXURE C) ---")
            appendLine(
                "fetch=${result.pipelineMs.fetch}ms decode=${result.pipelineMs.decode}ms " +
                    "asr=${result.pipelineMs.asr}ms embed=${result.pipelineMs.embed}ms vote=${result.pipelineMs.vote}ms"
            )
            val manifest = readManifest(context, result.callId)
            if (manifest?.preservedAudioSha256 != null) {
                appendLine("Preserved audio: ${manifest.preservedAudioFile} (${manifest.preservedAudioBytes} bytes)")
                appendLine("Audio SHA-256: ${manifest.preservedAudioSha256}")
            }
            appendLine()
            appendLine("Generated on-device by Rakshak Setu v1.3.0 — offline AI pipeline, no cloud processing.")
        }
        return manifestText
    }

    fun evidenceDir(context: Context, callId: String): File = File(context.filesDir, "evidence/$callId")

    private fun readManifest(context: Context, callId: String): EvidenceManifest? =
        try {
            val f = File(evidenceDir(context, callId), "manifest.json")
            if (f.exists()) Gson().fromJson(f.readText(), EvidenceManifest::class.java) else null
        } catch (e: Exception) {
            null
        }

    private fun guessExtension(context: Context, uri: android.net.Uri): String {
        return try {
            val mime = context.contentResolver.getType(uri)
            when {
                mime?.contains("amr") == true -> ".amr"
                mime?.contains("mp4") == true || mime?.contains("m4a") == true -> ".m4a"
                mime?.contains("mpeg") == true || mime?.contains("mp3") == true -> ".mp3"
                mime?.contains("ogg") == true -> ".ogg"
                mime?.contains("wav") == true -> ".wav"
                else -> ".audio"
            }
        } catch (e: Exception) {
            ".audio"
        }
    }

    private fun maskNumber(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < 4) return "+91XXXXXX0000"
        return "+91XXXXXX${digits.takeLast(4)}"
    }

    internal fun sha256String(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    internal fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n == -1) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Decouples the dossier exporter from the evidence package's own dependency graph. */
private object StatementGeneratorBridge {
    fun statement(context: Context, result: DetectionResult): String {
        return com.rakshaksetu.app.evidence.StatementGenerator.getEvidenceStatement(context, result.callId)
            ?: com.rakshaksetu.app.evidence.StatementGenerator.generate(result)
    }
}
