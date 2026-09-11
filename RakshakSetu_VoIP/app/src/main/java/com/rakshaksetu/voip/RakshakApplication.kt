package com.rakshaksetu.voip

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import com.rakshaksetu.voip.ai.AiPipelineCoordinator
import com.rakshaksetu.voip.ai.SpscAudioRingBuffer
import com.rakshaksetu.voip.evidence.Section65BManifest
import com.rakshaksetu.voip.telephony.CallNotificationManager
import com.rakshaksetu.voip.telephony.TelecomCallManager
import com.rakshaksetu.voip.telephony.VoipConnectionService
import com.rakshaksetu.voip.webrtc.WebRtcEngine

/**
 * Sovereign VoIP Application coordinator for Rakshak Setu Prototype 2.
 *
 * Coordinates:
 * 1. Self-managed PhoneAccount registration with Android TelecomManager (API 26+ / API 34+).
 * 2. In-memory lock-free SPSC circular ring buffer for zero-disk-I/O audio tapping.
 * 3. On-device dual edge AI pipeline (AASIST-L INT8 ONNX, Wald SPRT, Vosk ASR, Scam Trie).
 * 4. WebRTC DTLS-SRTP encrypted telephony engine.
 * 5. Section 65B Bharatiya Sakshya Adhiniyam (BSA) 2023 forensic manifest generator.
 */
open class RakshakApplication : Application() {

    companion object {
        private const val TAG = "RakshakApplication"
        const val PHONE_ACCOUNT_ID = "rakshak_sovereign_voip_account"
        const val PHONE_ACCOUNT_LABEL = "Rakshak Setu Sovereign VoIP"

        lateinit var instance: RakshakApplication
            private set
    }

    // Core Subsystems
    val audioRingBuffer: SpscAudioRingBuffer by lazy { SpscAudioRingBuffer() }

    lateinit var notificationManager: CallNotificationManager
        private set

    lateinit var telecomCallManager: TelecomCallManager
        private set

    lateinit var aiPipeline: AiPipelineCoordinator
        private set

    lateinit var webRtcEngine: WebRtcEngine
        private set

    lateinit var evidenceManifest: Section65BManifest
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.i(TAG, "Initializing Rakshak Setu Prototype 2 Sovereign Core...")

        // 1. Initialize Notification Channels (CallStyle incoming & ongoing)
        notificationManager = CallNotificationManager(this)

        // 2. Initialize and Register Self-Managed PhoneAccount with Android Telecom
        telecomCallManager = TelecomCallManager(this)
        registerSelfManagedPhoneAccount()

        // 3. Initialize AI Defense Pipeline (Vosk ASR + AASIST-L ONNX + Wald SPRT + Scam Trie)
        aiPipeline = AiPipelineCoordinator(this, audioRingBuffer)

        // 4. Initialize Sovereign WebRTC Telephony Engine (DTLS-SRTP + In-RAM PCM Tap)
        webRtcEngine = WebRtcEngine(this, audioRingBuffer)

        // 5. Initialize Section 65B Forensic Manifest Manager
        evidenceManifest = Section65BManifest(this)

        Log.i(TAG, "Rakshak Setu Sovereign Core successfully initialized.")
    }

    /**
     * Registers the sovereign self-managed PhoneAccount with Android TelecomManager.
     *
     * Invariant: Must strictly use CAPABILITY_SELF_MANAGED.
     * Do NOT combine with CAPABILITY_CALL_PROVIDER or CAPABILITY_CONNECTION_MANAGER,
     * as Android OS will reject it with an IllegalArgumentException.
     */
    private fun registerSelfManagedPhoneAccount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            if (telecomManager == null) {
                Log.w(TAG, "TelecomManager service unavailable on this device.")
                return
            }

            val phoneAccountHandle = PhoneAccountHandle(
                ComponentName(this, VoipConnectionService::class.java),
                PHONE_ACCOUNT_ID
            )

            val phoneAccount = PhoneAccount.builder(phoneAccountHandle, PHONE_ACCOUNT_LABEL)
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .setHighlightColor(Color.parseColor("#00E676")) // Emerald Safe Accent
                .setShortDescription("Encrypted DTLS-SRTP Sovereign Phone")
                .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
                .build()

            try {
                telecomManager.registerPhoneAccount(phoneAccount)
                val registeredAccount = telecomManager.getPhoneAccount(phoneAccountHandle)
                val isEnabled = registeredAccount?.isEnabled ?: false
                Log.i(TAG, "Self-Managed PhoneAccount registered. Enabled: $isEnabled")
            } catch (se: SecurityException) {
                Log.e(TAG, "SecurityException registering PhoneAccount: ${se.message}", se)
            } catch (iae: IllegalArgumentException) {
                Log.e(TAG, "IllegalArgumentException registering PhoneAccount: ${iae.message}", iae)
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error registering PhoneAccount: ${e.message}", e)
            }
        } else {
            Log.w(TAG, "Android Telecom Self-Managed API requires API 26 (Android 8.0) or higher.")
        }
    }
}

/**
 * Backward compatibility alias ensuring existing code referencing RakshakVoipApp continues
 * to compile and function with zero disruption.
 */
typealias RakshakVoipApp = RakshakApplication
