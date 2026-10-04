package com.rakshaksetu.app.webrtc

import android.content.Context
import android.util.Log
import com.rakshaksetu.app.audio.AudioInterceptionEngine
import com.rakshaksetu.app.audio.SpscAudioRingBuffer
import com.rakshaksetu.app.telecom.TelecomCallManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.UUID

/**
 * Stable, user-visible identity for this device on the signalling relay.
 *
 * The relay normalises every `clientId` through `normalizePhoneNumber`, and the
 * dialer only ever collects phone numbers. A random per-process id would
 * therefore make the device permanently unreachable: peers could never address
 * it. The identity is therefore persisted, seeded once, and settable by the user
 * so two devices can be paired by number.
 */
object SecureLineIdentity {
    private const val PREFS = "rakshak_secure_line"
    private const val KEY_ID = "identity"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }

        val seeded = "rakshak-" + UUID.randomUUID().toString().take(8)
        prefs.edit().putString(KEY_ID, seeded).apply()
        return seeded
    }

    fun set(context: Context, identity: String) {
        val cleaned = identity.trim()
        if (cleaned.isEmpty()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ID, cleaned).apply()
    }
}

/**
 * Single composition root for a sovereign WebRTC call between two devices.
 *
 * WHY THIS EXISTS
 * ---------------
 * WebRtcEngine, SignalingClient, the Telecom ConnectionService and the live-AI
 * coordinator were all implemented and reachable from the navigation graph
 * (Screen.SecureLine / VoipDialer / VoipIncomingCall / VoipActiveHud), but
 * nothing ever CONSTRUCTED them. Grepping for `WebRtcEngine(`,
 * `TelecomCallManager(` and `AiPipelineCoordinator(` returned only the class
 * declarations -- the stack was dead code behind a nav route.
 *
 *   SignalingClient --(WebSocket SDP/ICE)--> peer device
 *            |
 *            v
 *   WebRtcEngine --(DTLS-SRTP audio)--> SpscAudioRingBuffer
 *            |                                    |
 *            |                                    v
 *            |                      AudioInterceptionEngine
 *            |                                    |
 *            |                                    v
 *            |                      AiPipelineCoordinator (live threat HUD)
 *            v
 *   TelecomCallManager (self-managed PhoneAccount, system call UI)
 *
 * SCOPE: end-to-end encrypted (DTLS-SRTP) prototype demonstrating in-call
 * protection between two cooperating devices. Operating a public VoIP service
 * in India is regulated under the TELECOMS (Licensing) Regulations 2021; this
 * is not a licensable carrier product.
 */
class VoipSessionManager private constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "VoipSession"

        @Volatile private var instance: VoipSessionManager? = null

        fun get(context: Context): VoipSessionManager =
            instance ?: synchronized(this) {
                instance ?: VoipSessionManager(context.applicationContext).also { instance = it }
            }

        fun reset() {
            synchronized(this) {
                instance?.release()
                instance = null
            }
        }
    }

    enum class CallState { IDLE, CONNECTING, RINGING, CONNECTED, ENDED, FAILED }

    data class UiState(
        val state: CallState = CallState.IDLE,
        val peerId: String = "",
        val isOutgoing: Boolean = false,
        val error: String? = null,
        val threatLevel: String = "No threat detected"
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Built lazily so an app that never places a VoIP call pays nothing for it.
    private val ringBuffer: SpscAudioRingBuffer by lazy { SpscAudioRingBuffer() }
    private val interception: AudioInterceptionEngine by lazy { AudioInterceptionEngine(ringBuffer) }

    private var signaling: SignalingClient? = null
    private var activeUrl: String? = null
    private var engine: WebRtcEngine? = null
    private var telecom: TelecomCallManager? = null

    private val localId: String
        get() = SecureLineIdentity.get(context)

    /**
     * Register this device under [identity] instead of the generated fallback.
     *
     * The dialer addresses peers by phone number, and the relay normalises every
     * `clientId` through `normalizePhoneNumber`, so a device is only reachable if
     * it REGISTERED with a phone number too. Without this the offer is routed to
     * `+91...` while the peer sits in the registry under `rakshak-ab12cd34`, the
     * relay replies `peer_offline`, and no call is ever negotiated.
     */
    fun setLocalIdentity(identity: String) {
        SecureLineIdentity.set(context, identity)
        if (signaling != null) {
            // Re-register so the relay learns the new identity on the live socket.
            signaling?.connect(localId)
        }
    }

    /** Connect the signalling channel and register this device. */
    fun connect(serverUrl: String) {
        // Already connected to the same relay: keep the existing socket alive.
        // Re-connecting would call release() below and tear down a live
        // registration (and any call in progress) every time the dialer screen
        // is opened, because connect() now also runs once at app startup.
        if (signaling != null && activeUrl == serverUrl) {
            Log.i(TAG, "connect skipped: already registered as $localId")
            return
        }
        activeUrl = serverUrl
        release()
        _uiState.update { it.copy(state = CallState.CONNECTING, error = null) }
        signaling = SignalingClient(serverUrl, signalingListener()).also { it.connect(localId) }
    }

    private fun signalingListener() = object : SignalingClient.Listener {
        override fun onConnected() {
            Log.i(TAG, "Signalling connected as $localId")
            _uiState.update { it.copy(state = CallState.IDLE) }
        }

        override fun onDisconnected() {
            _uiState.update { it.copy(state = CallState.ENDED) }
        }

        override fun onOfferReceived(from: String, sdp: String) {
            _uiState.update { it.copy(state = CallState.RINGING, peerId = from, isOutgoing = false) }
            buildEngine()?.handleRemoteOffer(sdp)
        }

        override fun onAnswerReceived(from: String, sdp: String) {
            _uiState.update { it.copy(state = CallState.CONNECTED, peerId = from) }
            engine?.handleRemoteAnswer(sdp)
        }

        override fun onCandidateReceived(from: String, mid: String, index: Int, sdp: String) {
            engine?.addRemoteIceCandidate(mid, index, sdp)
        }

        override fun onHangupReceived(from: String, reason: String) {
            Log.i(TAG, "Hangup from $from ($reason)")
            endCall()
        }

        override fun onError(message: String) {
            _uiState.update { it.copy(state = CallState.FAILED, error = message) }
        }
    }
    // callEvents / startOutgoingCall / acceptIncoming / endCall / release follow.

    private fun callEvents() = object : WebRtcEngine.CallEvents {
        override fun onCallConnected() {
            _uiState.update { it.copy(state = CallState.CONNECTED) }
        }

        override fun onCallDisconnected(reason: String) {
            _uiState.update { it.copy(state = CallState.ENDED) }
        }

        override fun onIceCandidateGenerated(candidate: IceCandidate) {
            val peer = _uiState.value.peerId
            if (peer.isNotBlank()) {
                signaling?.sendCandidate(peer, candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }
        }

        override fun onLocalSdpCreated(sdp: SessionDescription) {
            val peer = _uiState.value.peerId
            Log.i(TAG, "onLocalSdpCreated: peer='$peer' outgoing=${_uiState.value.isOutgoing} signalingNull=${signaling == null}")
            if (peer.isBlank()) return
            // The role decides the message: whoever was called must answer.
            if (_uiState.value.isOutgoing) signaling?.sendOffer(peer, sdp.description)
            else signaling?.sendAnswer(peer, sdp.description)
        }
    }

    /** Place a call to [peerId] — the other device's signalling id. */
    fun startOutgoingCall(peerId: String) {
        if (signaling == null) {
            _uiState.update { it.copy(state = CallState.FAILED, error = "Not connected to signalling") }
            return
        }
        _uiState.update {
            it.copy(state = CallState.CONNECTING, peerId = peerId, isOutgoing = true, error = null)
        }
        buildEngine()?.also { e ->
            e.callEvents = callEvents()
            Log.i(TAG, "startOutgoingCall peer='$peerId' engineReady=${e.isReady()}")
            e.startCall(isInitiator = true)
        }
    }

    /** Answer an inbound call. */
    fun acceptIncoming() {
        buildEngine()?.also { e ->
            e.callEvents = callEvents()
            e.startCall(isInitiator = false)
        }
        registerTelecomIfPermitted()
    }

    fun endCall() {
        val peer = _uiState.value.peerId
        if (peer.isNotBlank()) signaling?.sendHangup(peer)
        engine?.terminateCall()
        interception.stopInterception()
        _uiState.update { it.copy(state = CallState.ENDED) }
    }

    /** Tear everything down. Safe to call repeatedly. */
    fun release() {
        try { interception.stopInterception() } catch (ignored: Exception) {}
        try { engine?.terminateCall() } catch (ignored: Exception) {}
        try { signaling?.disconnect() } catch (ignored: Exception) {}
        engine = null
        signaling = null
// Clear the cached relay URL so a later connect() to the same endpoint is not
        // skipped by the already-connected guard.
        activeUrl = null
        telecom = null
    }

    /** This device's signalling id, shown in the dialer for the peer to type in. */
    fun localDeviceId(): String = localId

    /** Exposed so the live-AI HUD can attach a coordinator to the same buffer. */
    fun audioRingBuffer(): SpscAudioRingBuffer = ringBuffer

    private fun buildEngine(): WebRtcEngine? {
        engine?.let { return it }
        return WebRtcEngine(context, ringBuffer).also {
            engine = it
            interception.startInterception()
        }
    }

    /**
     * Register a self-managed PhoneAccount so the call appears in the system UI.
     *
     * MANAGE_OWN_CALLS is a normal runtime permission, but this integration is
     * best-effort: a failure must never break the P2P call, so the exception is
     * swallowed deliberately.
     */
    private fun registerTelecomIfPermitted() {
        try {
            telecom = TelecomCallManager(context).also {
                it.reportIncomingCall(_uiState.value.peerId.ifBlank { "Encrypted Peer" })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Telecom integration unavailable (MANAGE_OWN_CALLS?): ${e.message}")
        }
    }
}