package com.rakshaksetu.app.webrtc

import org.webrtc.audio.JavaAudioDeviceModule

/**
 * Interface and callback bridge for tapping decoded linear PCM audio frames
 * from WebRTC's JavaAudioDeviceModule directly into RAM.
 */
fun interface WebRtcAudioRecordSamplesReadyCallback {
    fun onWebRtcAudioRecordSamplesReady(audioSamples: JavaAudioDeviceModule.AudioSamples)
}
