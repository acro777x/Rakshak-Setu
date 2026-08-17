package com.rakshaksetu.app.pipeline

import java.io.File
import java.io.FileInputStream

data class AudioSegment(
    val index: Int,
    val startSec: Int,
    val pcmData: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioSegment

        if (index != other.index) return false
        if (startSec != other.startSec) return false
        if (!pcmData.contentEquals(other.pcmData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + startSec
        result = 31 * result + pcmData.contentHashCode()
        return result
    }
}

object Segmenter {
    private const val CHUNK_SIZE_BYTES = 160_000 // 5 seconds at 16kHz, 16-bit, mono
    private const val WAV_HEADER_SIZE = 44

    /**
     * Splits a 16kHz 16-bit mono WAV file into 5-second non-overlapping chunks.
     * Skips the 44-byte WAV header.
     *
     * @param wavFile The processed WAV file from AudioDecoder
     * @return A list of AudioSegments, each containing up to 5s of PCM data
     */
    fun segmentAudio(wavFile: File): List<AudioSegment> {
        val segments = mutableListOf<AudioSegment>()
        if (!wavFile.exists()) return segments

        FileInputStream(wavFile).use { fis ->
            // Skip WAV header
            val skipped = fis.skip(WAV_HEADER_SIZE.toLong())
            if (skipped < WAV_HEADER_SIZE) return segments // invalid or too small

            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            var bytesRead: Int
            var index = 0

            while (fis.read(buffer).also { bytesRead = it } != -1) {
                // If we didn't read a full chunk, we still process the remaining audio
                val pcmData = if (bytesRead == CHUNK_SIZE_BYTES) {
                    buffer.clone()
                } else {
                    buffer.copyOf(bytesRead)
                }

                segments.add(
                    AudioSegment(
                        index = index,
                        startSec = index * 5,
                        pcmData = pcmData
                    )
                )
                index++
            }
        }
        
        return segments
    }
}
