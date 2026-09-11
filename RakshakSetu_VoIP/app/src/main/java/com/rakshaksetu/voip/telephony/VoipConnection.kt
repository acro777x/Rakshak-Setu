package com.rakshaksetu.voip.telephony

import android.telecom.Connection
import android.telecom.DisconnectCause
import android.util.Log

/**
 * Telecom Connection implementation for Rakshak Setu Self-Managed VoIP Calls.
 */
open class VoipConnection(
    val peerIdentifier: String,
    val isIncoming: Boolean,
    private val callbacks: Listener
) : Connection() {

    companion object {
        private const val TAG = "VoipConnection"
    }

    interface Listener {
        fun onCallAnswered(connection: VoipConnection)
        fun onCallRejected(connection: VoipConnection)
        fun onCallDisconnected(connection: VoipConnection)
        fun onCallHoldStateChanged(connection: VoipConnection, onHold: Boolean)
    }

    init {
        connectionCapabilities = CAPABILITY_HOLD or CAPABILITY_SUPPORT_HOLD
        audioModeIsVoip = true

        if (isIncoming) {
            setRinging()
        } else {
            setDialing()
        }
    }

    override fun onAnswer() {
        Log.i(TAG, "Telecom onAnswer: $peerIdentifier")
        setActive()
        callbacks.onCallAnswered(this)
    }

    override fun onReject() {
        Log.i(TAG, "Telecom onReject: $peerIdentifier")
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
        callbacks.onCallRejected(this)
    }

    override fun onDisconnect() {
        Log.i(TAG, "Telecom onDisconnect: $peerIdentifier")
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
        callbacks.onCallDisconnected(this)
    }

    override fun onHold() {
        Log.i(TAG, "Telecom onHold: $peerIdentifier")
        setOnHold()
        callbacks.onCallHoldStateChanged(this, true)
    }

    override fun onUnhold() {
        Log.i(TAG, "Telecom onUnhold: $peerIdentifier")
        setActive()
        callbacks.onCallHoldStateChanged(this, false)
    }
}
