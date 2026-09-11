package com.rakshaksetu.voip.telephony

import android.content.Context
import com.rakshaksetu.voip.telecom.CallForegroundService as TelecomCallForegroundService

/**
 * Backward compatibility subclass for CallForegroundService.
 * Canonical implementation resides in com.rakshaksetu.voip.telecom.CallForegroundService.
 */
class CallForegroundService : TelecomCallForegroundService() {
    companion object {
        const val ACTION_START_CALL = TelecomCallForegroundService.ACTION_START_CALL
        const val ACTION_STOP_CALL = TelecomCallForegroundService.ACTION_STOP_CALL
        const val EXTRA_PEER_ID = TelecomCallForegroundService.EXTRA_PEER_ID

        fun startService(context: Context, peerId: String) {
            TelecomCallForegroundService.startService(context, peerId)
        }

        fun stopService(context: Context) {
            TelecomCallForegroundService.stopService(context)
        }
    }
}
