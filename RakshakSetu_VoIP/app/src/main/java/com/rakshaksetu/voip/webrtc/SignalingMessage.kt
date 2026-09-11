package com.rakshaksetu.voip.webrtc

/**
 * Data structures for sovereign peer-to-peer WebRTC signaling protocol.
 */
sealed class SignalingMessage {
    abstract val type: String

    data class Register(
        val clientId: String,
        override val type: String = "register"
    ) : SignalingMessage()

    data class Offer(
        val from: String,
        val to: String,
        val sdp: String,
        override val type: String = "offer"
    ) : SignalingMessage()

    data class Answer(
        val from: String,
        val to: String,
        val sdp: String,
        override val type: String = "answer"
    ) : SignalingMessage()

    data class Candidate(
        val from: String,
        val to: String,
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val sdpCandidate: String,
        override val type: String = "candidate"
    ) : SignalingMessage()

    data class Hangup(
        val from: String,
        val to: String,
        val reason: String = "normal_clearing",
        override val type: String = "hangup"
    ) : SignalingMessage()
}
