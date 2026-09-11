package com.rakshaksetu.voip.telecom

import android.content.Intent
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.util.Log
import com.rakshaksetu.voip.telephony.VoipConnection

/**
 * Self-Managed ConnectionService integration for Android 14 TelecomManager.
 * Manages VoIP call lifecycle with Android system audio routing and notifications.
 */
open class RakshakConnectionService : ConnectionService(), VoipConnection.Listener {

    companion object {
        private const val TAG = "RakshakConnectionService"
        var currentConnection: VoipConnection? = null
            protected set
        var activeListener: VoipConnection.Listener? = null
    }

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val caller = request?.address?.schemeSpecificPart ?: "Encrypted Peer"
        Log.i(TAG, "onCreateIncomingConnection: $caller")

        val connection = RakshakCallConnection(caller, isIncoming = true, this)
        currentConnection = connection
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        val callee = request?.address?.schemeSpecificPart ?: "Encrypted Peer"
        Log.i(TAG, "onCreateOutgoingConnection: $callee")

        val connection = RakshakCallConnection(callee, isIncoming = false, this)
        currentConnection = connection
        return connection
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateIncomingConnectionFailed")
        currentConnection = null
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ) {
        Log.w(TAG, "onCreateOutgoingConnectionFailed")
        currentConnection = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            IncomingCallNotificationManager.ACTION_ANSWER -> {
                currentConnection?.onAnswer()
            }
            IncomingCallNotificationManager.ACTION_DECLINE -> {
                currentConnection?.onReject()
            }
            IncomingCallNotificationManager.ACTION_HANGUP -> {
                currentConnection?.onDisconnect()
            }
        }
        return START_NOT_STICKY
    }

    override fun onCallAnswered(connection: VoipConnection) {
        activeListener?.onCallAnswered(connection)
    }

    override fun onCallRejected(connection: VoipConnection) {
        activeListener?.onCallRejected(connection)
        currentConnection = null
    }

    override fun onCallDisconnected(connection: VoipConnection) {
        activeListener?.onCallDisconnected(connection)
        currentConnection = null
    }

    override fun onCallHoldStateChanged(connection: VoipConnection, onHold: Boolean) {
        activeListener?.onCallHoldStateChanged(connection, onHold)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentConnection = null
    }
}
