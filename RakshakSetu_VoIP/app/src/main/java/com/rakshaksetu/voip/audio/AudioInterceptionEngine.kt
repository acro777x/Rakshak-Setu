package com.rakshaksetu.voip.audio

import android.util.Log

/**
 * Coordinates in-memory audio tap and streaming decimation from WebRTC playout
 * into the lock-free SPSC circular ring buffer without disk I/O.
 */
class AudioInterceptionEngine(
    val ringBuffer: SpscAudioRingBuffer
) {
    companion object {
        private const val TAG = "AudioInterceptionEngine"
    }

    private var isIntercepting = false

    fun startInterception() {
        isIntercepting = true
        Log.i(TAG, "In-memory RAM audio interception started.")
    }

    fun stopInterception() {
        isIntercepting = false
        Log.i(TAG, "In-memory RAM audio interception stopped.")
    }

    fun interceptPcmFrames(pcmData: ShortArray, length: Int, sampleRate: Int, channels: Int) {
        if (!isIntercepting) return
        ringBuffer.write(pcmData, 0, length)
    }

    fun interceptPcmBytes(pcmBytes: ByteArray, offset: Int = 0, length: Int = pcmBytes.size) {
        if (!isIntercepting) return
        ringBuffer.write(pcmBytes, offset, length)
    }
}
