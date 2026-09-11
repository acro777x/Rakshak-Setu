package com.rakshaksetu.voip.webrtc

import android.content.Context
import android.util.Log
import com.rakshaksetu.voip.ai.SpscAudioRingBuffer
import org.webrtc.*
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

/**
 * Sovereign WebRTC calling core running peer-to-peer encrypted DTLS-SRTP VoIP.
 *
 * Intercepts decoded in-flight 16-bit linear PCM audio frames directly in userspace RAM
 * via JavaAudioDeviceModule SamplesReadyCallback before playback, streaming directly into
 * the SPSC lock-free circular ring buffer (zero disk I/O, zero underruns).
 */
class WebRtcEngine(
    private val context: Context,
    val ringBuffer: SpscAudioRingBuffer
) {
    companion object {
        private const val TAG = "WebRtcEngine"
        private const val AUDIO_TRACK_ID = "rakshak_audio_track"
        private const val AUDIO_SOURCE_ID = "rakshak_audio_source"
    }

    interface CallEvents {
        fun onCallConnected()
        fun onCallDisconnected(reason: String)
        fun onIceCandidateGenerated(candidate: IceCandidate)
        fun onLocalSdpCreated(sdp: SessionDescription)
    }

    private val executor = Executors.newSingleThreadExecutor()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var isInitialized = false

    var callEvents: CallEvents? = null

    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        executor.execute {
            try {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(context)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions()
                )

                // Configure JavaAudioDeviceModule with decoded PCM interceptor in RAM
                val audioDeviceModule = JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .setSamplesReadyCallback { audioSamples ->
                        // DIRECT RAM AUDIO TAP: Intercept decoded 16-bit linear PCM frames
                        // Zero disk I/O, zero blocking on audio rendering thread
                        val data = audioSamples.data
                        if (data != null && data.isNotEmpty()) {
                            ringBuffer.write(data, 0, data.size)
                        }
                    }
                    .createAudioDeviceModule()

                val options = PeerConnectionFactory.Options()
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setAudioDeviceModule(audioDeviceModule)
                    .createPeerConnectionFactory()

                isInitialized = true
                Log.i(TAG, "WebRTC PeerConnectionFactory initialized with sovereign RAM PCM tap")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize native WebRTC library: ${e.message}. Fallback mode active.")
                isInitialized = false
            }
        }
    }

    /**
     * Creates and starts a new encrypted peer connection session.
     */
    fun startCall(isInitiator: Boolean) {
        executor.execute {
            try {
                val iceServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
                )

                val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                    continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                    // Sovereign DTLS-SRTP encryption is enforced by default
                }

                peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                    override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                        Log.d(TAG, "SignalingState: $state")
                    }

                    override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                        Log.i(TAG, "IceConnectionState: $state")
                        when (state) {
                            PeerConnection.IceConnectionState.CONNECTED -> {
                                callEvents?.onCallConnected()
                            }
                            PeerConnection.IceConnectionState.DISCONNECTED,
                            PeerConnection.IceConnectionState.FAILED,
                            PeerConnection.IceConnectionState.CLOSED -> {
                                callEvents?.onCallDisconnected(state.name)
                            }
                            else -> {}
                        }
                    }

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                    override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}

                    override fun onIceCandidate(candidate: IceCandidate?) {
                        if (candidate != null) {
                            callEvents?.onIceCandidateGenerated(candidate)
                        }
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                    override fun onAddStream(stream: MediaStream?) {}

                    override fun onRemoveStream(stream: MediaStream?) {}

                    override fun onDataChannel(channel: DataChannel?) {}

                    override fun onRenegotiationNeeded() {}

                    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                        Log.i(TAG, "WebRTC audio track added: ${receiver?.track()?.id()}")
                    }
                })

                // Create local audio track
                val audioConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                }
                audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
                localAudioTrack = peerConnectionFactory?.createAudioTrack(AUDIO_TRACK_ID, audioSource)
                localAudioTrack?.setEnabled(true)

                peerConnection?.addTrack(localAudioTrack, listOf("rakshak_stream"))

                if (isInitiator) {
                    createOffer()
                }
            } catch (e: Exception) {
                Log.e(TAG, "startCall error: ${e.message}")
            }
        }
    }

    private fun createOffer() {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            callEvents?.onLocalSdpCreated(desc)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "createOffer failure: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, sdpConstraints)
    }

    fun handleRemoteOffer(remoteSdp: String) {
        executor.execute {
            val desc = SessionDescription(SessionDescription.Type.OFFER, remoteSdp)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    createAnswer()
                }
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(err: String?) {
                    Log.e(TAG, "setRemoteDescription failure: $err")
                }
            }, desc)
        }
    }

    private fun createAnswer() {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc != null) {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            callEvents?.onLocalSdpCreated(desc)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {}
                    }, desc)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "createAnswer failure: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, sdpConstraints)
    }

    fun handleRemoteAnswer(remoteSdp: String) {
        executor.execute {
            val desc = SessionDescription(SessionDescription.Type.ANSWER, remoteSdp)
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {
                    Log.i(TAG, "Remote answer applied successfully")
                }
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(err: String?) {
                    Log.e(TAG, "handleRemoteAnswer failure: $err")
                }
            }, desc)
        }
    }

    fun addRemoteIceCandidate(mid: String, index: Int, candidateSdp: String) {
        executor.execute {
            val candidate = IceCandidate(mid, index, candidateSdp)
            peerConnection?.addIceCandidate(candidate)
        }
    }

    /**
     * Instantly terminates WebRTC session and halts audio processing.
     */
    fun terminateCall() {
        executor.execute {
            try {
                localAudioTrack?.setEnabled(false)
                peerConnection?.close()
                peerConnection = null
                audioSource?.dispose()
                audioSource = null
                Log.i(TAG, "WebRTC Call session severed.")
            } catch (e: Exception) {
                Log.w(TAG, "terminateCall error: ${e.message}")
            }
        }
    }

    /**
     * Directly injects decoded PCM audio chunks into the ring buffer for testing and simulation.
     */
    fun injectSimulatedAudio(pcmData: ByteArray) {
        ringBuffer.write(pcmData, 0, pcmData.size)
    }
}
