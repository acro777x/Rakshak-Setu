package com.rakshaksetu.app.pipeline

import android.content.Context
import android.util.Log
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.FlaggedSegment
import com.rakshaksetu.app.model.PipelineMs
import java.io.File
import java.util.UUID

class PipelineCoordinator(
    private val context: Context,
    private val whisperEngine: WhisperEngine,
    private val votingEngine: VotingEngine
) {
    companion object {
        private const val TAG = "PipelineCoordinator"
    }

    /**
     * Executes the full pipeline for a recently finished call.
     * @param callId UUID of the call
     * @param phoneNumber The remote phone number
     * @param callDurationSec Duration of the call in seconds
     * @param callEndEpoch When the call ended
     * @param oemPaths List of directory paths to check for audio recordings if MediaStore fails
     * @param destWavPath Path where the normalized WAV should be saved temporarily
     */
    suspend fun runPipeline(
        callId: String = UUID.randomUUID().toString(),
        phoneNumber: String,
        callDurationSec: Int,
        callEndEpoch: Long,
        oemPaths: List<String>,
        destWavPath: String
    ): DetectionResult? {
        val tStart = System.currentTimeMillis()
        var tFetchEnd = 0L
        var tDecodeEnd = 0L
        var tAsrTotal = 0L
        var tEmbedTotal = 0L
        var tVoteTotal = 0L

        // A1: Fetch
        val audioUri = AudioFetcher.fetchWithRetries(context, callEndEpoch, oemPaths)
        if (audioUri == null) {
            Log.e(TAG, "Failed to fetch audio recording for call.")
            return null
        }
        tFetchEnd = System.currentTimeMillis()

        // We need the actual path to pass to FFmpeg
        // For simplicity in this mock, we assume audioUri.path gives us a valid file path or we resolve it
        val srcPath = resolveUriToPath(audioUri) 

        // A2: Decode & Normalize
        val decodeSuccess = AudioDecoder.decodeToWav(srcPath, destWavPath)
        if (!decodeSuccess) {
            Log.e(TAG, "Failed to decode audio to WAV.")
            return null
        }
        tDecodeEnd = System.currentTimeMillis()

        // A4: Segment (and A3 VAD within)
        val segments = Segmenter.segmentAudio(File(destWavPath))
        
        val fullTranscriptBuilder = StringBuilder()
        val processedSegments = mutableListOf<SegmentResult>()

        for (seg in segments) {
            // A3: VAD Gate
            if (!VadGate.isVoiceActive(seg.pcmData)) {
                Log.d(TAG, "Segment ${seg.index} is EMPTY (silence/noise). Skipping ASR.")
                continue
            }

            // A5: ASR
            val tAsrStart = System.currentTimeMillis()
            val transcript = whisperEngine.transcribe(seg.pcmData)
            tAsrTotal += (System.currentTimeMillis() - tAsrStart)

            fullTranscriptBuilder.append(transcript).append(" ")

            // A7: Embeddings & Similarity matching
            val tEmbedStart = System.currentTimeMillis()
            val (similarity, matchedCategory) = EmbeddingEngine.findBestMatch(transcript)
            tEmbedTotal += (System.currentTimeMillis() - tEmbedStart)

            val segmentResult = SegmentResult(
                index = seg.index,
                startSec = seg.startSec,
                text = transcript.trim(),
                similarity = similarity,
                matchedCategory = matchedCategory
            )
            processedSegments.add(segmentResult)

            // A8: A4 Voting (run cumulatively or just once at the end)
            // The pipeline can technically stream verdicts, but we run it at the end for the final JSON contract
        }

        val tVoteStart = System.currentTimeMillis()
        val verdict = votingEngine.evaluate(processedSegments)
        tVoteTotal += (System.currentTimeMillis() - tVoteStart)

        // A9: Create DetectionResult Contract
        val flaggedSegments = verdict.hits.map {
            FlaggedSegment(
                index = it.index,
                startSec = it.startSec,
                text = it.text,
                similarity = it.similarity,
                matchedCategory = it.matchedCategory ?: "unknown"
            )
        }

        val pipelineMs = PipelineMs(
            fetch = tFetchEnd - tStart,
            decode = tDecodeEnd - tFetchEnd,
            asr = tAsrTotal,
            embed = tEmbedTotal,
            vote = tVoteTotal
        )

        return DetectionResult(
            callId = callId,
            phoneNumber = phoneNumber,
            callEndEpoch = callEndEpoch,
            durationSec = callDurationSec,
            audioUri = audioUri.toString(),
            isScam = verdict.isScam,
            confidence = verdict.confidence,
            scamType = verdict.scamType,
            flaggedSegments = flaggedSegments,
            fullTranscript = fullTranscriptBuilder.toString().trim(),
            pipelineMs = pipelineMs
        )
    }

    private fun resolveUriToPath(uri: android.net.Uri): String {
        // Mock URI resolution to actual file path for FFmpeg.
        // In reality, you'd use ContentResolver to copy the file to a cache dir if you can't get a direct path.
        return uri.path ?: ""
    }
}
