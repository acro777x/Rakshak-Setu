package com.rakshaksetu.voip.webrtc

/**
 * Interface contract for tapping decrypted audio frames in RAM before speaker playout.
 */
interface AudioInterceptor {
    fun onDecryptedAudioFrame(pcmData: ShortArray, length: Int, sampleRate: Int, channels: Int)
}

/**
 * Playout sink intercepting decrypted 16-bit linear PCM audio frames.
 */
class VoipAudioSink(
    private val interceptor: AudioInterceptor? = null
) {
    fun onFrame(data: ShortArray, length: Int, sampleRate: Int, channels: Int) {
        interceptor?.onDecryptedAudioFrame(data, length, sampleRate, channels)
    }
}
