package com.rakshaksetu.voip.telephony

import com.rakshaksetu.voip.telecom.RakshakConnectionService

/**
 * Backward compatibility subclass for VoipConnectionService.
 * Canonical implementation resides in com.rakshaksetu.voip.telecom.RakshakConnectionService.
 */
class VoipConnectionService : RakshakConnectionService() {
    companion object {
        val currentConnection: VoipConnection?
            get() = RakshakConnectionService.currentConnection

        var activeListener: VoipConnection.Listener?
            get() = RakshakConnectionService.activeListener
            set(value) {
                RakshakConnectionService.activeListener = value
            }
    }
}
