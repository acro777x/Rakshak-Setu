package com.rakshaksetu.app.pipeline

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AudioDecoder {
    private const val TAG = "AudioDecoder"

    /**
     * Decodes and normalizes the input audio file to 16kHz, mono, 16-bit PCM WAV.
     * Required format for Whisper ASR input.
     *
     * @param srcPath Path to the source audio file (e.g. mp3/amr/m4a from OEM dialer)
     * @param destPath Path where the WAV file will be saved
     * @return true if decoding was successful, false otherwise
     */
    suspend fun decodeToWav(srcPath: String, destPath: String): Boolean = withContext(Dispatchers.IO) {
        val destFile = File(destPath)
        if (destFile.exists()) {
            destFile.delete()
        }

        val command = "-i \"$srcPath\" -ar 16000 -ac 1 -acodec pcm_s16le \"$destPath\""
        Log.d(TAG, "Executing FFmpeg command: $command")
        
        val session = FFmpegKit.execute(command)
        val returnCode = session.returnCode

        if (ReturnCode.isSuccess(returnCode)) {
            Log.i(TAG, "FFmpeg decoding succeeded. File saved to $destPath")
            return@withContext true
        } else {
            Log.e(TAG, "FFmpeg decoding failed with return code $returnCode, output: ${session.failStackTrace}")
            return@withContext false
        }
    }
}
