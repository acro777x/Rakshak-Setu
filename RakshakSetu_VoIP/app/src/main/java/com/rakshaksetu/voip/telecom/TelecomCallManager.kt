package com.rakshaksetu.voip.telecom

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log

/**
 * High-level TelecomManager controller that registers self-managed PhoneAccounts
 * and coordinates system call integration.
 */
class TelecomCallManager(private val context: Context) {

    companion object {
        private const val TAG = "TelecomCallManager"
        const val PHONE_ACCOUNT_ID = "rakshak_sovereign_voip_account"
    }

    private val telecomManager =
        context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val phoneAccountHandle: PhoneAccountHandle = PhoneAccountHandle(
        ComponentName(context, RakshakConnectionService::class.java),
        PHONE_ACCOUNT_ID
    )

    init {
        registerPhoneAccount()
    }

    /**
     * Registers self-managed PhoneAccount with Android Telecom.
     * Invariant: Must strictly use PhoneAccount.CAPABILITY_SELF_MANAGED.
     */
    fun registerPhoneAccount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val account = PhoneAccount.builder(phoneAccountHandle, "Rakshak Setu Sovereign VoIP")
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .setHighlightColor(0x00E676)
                .setShortDescription("Encrypted DTLS-SRTP Phone")
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                .build()

            try {
                telecomManager.registerPhoneAccount(account)
                Log.i(TAG, "PhoneAccount registered successfully with CAPABILITY_SELF_MANAGED")
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException registering PhoneAccount: ${e.message}")
            }
        }
    }

    /**
     * Notify Android Telecom of a new incoming VoIP call.
     */
    fun reportIncomingCall(callerIdentifier: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val extras = Bundle().apply {
                val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, callerIdentifier, null)
                putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, uri)
            }
            try {
                telecomManager.addNewIncomingCall(phoneAccountHandle, extras)
                Log.i(TAG, "Reported incoming call for $callerIdentifier to TelecomManager")
            } catch (e: Exception) {
                Log.w(TAG, "TelecomManager.addNewIncomingCall error: ${e.message}")
            }
        }
    }

    /**
     * Place an outgoing call through TelecomManager.
     */
    fun placeOutgoingCall(calleeIdentifier: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val uri = Uri.fromParts(PhoneAccount.SCHEME_TEL, calleeIdentifier, null)
            val extras = Bundle().apply {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
            }
            try {
                telecomManager.placeCall(uri, extras)
                Log.i(TAG, "Placed outgoing call to $calleeIdentifier via TelecomManager")
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException placing call: ${e.message}")
            }
        }
    }

    /**
     * Configures Android AudioManager for VoIP communication.
     */
    fun configureAudioForCall(speakerOn: Boolean = false) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = speakerOn
        Log.i(TAG, "AudioManager configured: MODE_IN_COMMUNICATION, speaker=$speakerOn")
    }

    /**
     * Resets audio mode when call ends.
     */
    fun resetAudioMode() {
        audioManager.mode = AudioManager.MODE_NORMAL
        audioManager.isSpeakerphoneOn = false
    }
}
