package com.rakshaksetu.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rakshaksetu.app.telephony.RakshakCallStateListener

/**
 * Re-registers telephony listener after device reboot.
 * Without this, call-end detection stops until user manually opens app.
 */
class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed — re-registering telephony listener")
            RakshakCallStateListener.register(context.applicationContext)
        }
    }
}
