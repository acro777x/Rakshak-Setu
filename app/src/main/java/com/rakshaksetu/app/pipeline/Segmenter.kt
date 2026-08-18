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
     * Splits a 16kHz 16-bit mono WAV file into 5-second chunks with a 1-second overlap.
     * Skips the 44-byte WAV header.
     *
     * @param wavFile The processed WAV file from AudioDecoder
     * @return A list of AudioSegments, each containing up to 5s of PCM data
     */
    fun segmentAudio(wavFile: File): List<AudioSegment> {
        val segments = mutableListOf<AudioSegment>()
        if (!wavFile.exists()) return segments

        val OVERLAP_BYTES = 32_000 // 1 second at 16kHz, 16-bit, mono
        val ADVANCE_BYTES = CHUNK_SIZE_BYTES - OVERLAP_BYTES // 4 seconds

        java.io.RandomAccessFile(wavFile, "r").use { raf ->
            // Skip WAV header
            if (raf.length() <= WAV_HEADER_SIZE) return segments
            raf.seek(WAV_HEADER_SIZE.toLong())

            val buffer = ByteArray(CHUNK_SIZE_BYTES)
            var index = 0
            var startSec = 0

            while (raf.filePointer < raf.length()) {
                val bytesRead = raf.read(buffer)
                if (bytesRead == -1) break

                val pcmData = if (bytesRead == CHUNK_SIZE_BYTES) {
                    buffer.clone()
                } else {
                    buffer.copyOf(bytesRead)
                }

                segments.add(
                    AudioSegment(
                        index = index,
                        startSec = startSec,
                        pcmData = pcmData
                    )
                )

                // Advance by 4 seconds (so we have a 1 second overlap)
                // We calculate the next position based on where we started reading this chunk
                val nextStartPos = raf.filePointer - bytesRead + ADVANCE_BYTES
                if (nextStartPos < raf.length()) {
                    raf.seek(nextStartPos)
                }
                
                index++
                startSec += 4 // startSec advances by 4 seconds each chunk
            }
        }
        
        return segments
    }
}
