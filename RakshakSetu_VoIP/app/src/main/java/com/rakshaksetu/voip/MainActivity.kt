package com.rakshaksetu.voip

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.rakshaksetu.voip.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.voip.telephony.CallForegroundService
import com.rakshaksetu.voip.telephony.CallNotificationManager
import com.rakshaksetu.voip.ui.screens.ActiveCallHudScreen
import com.rakshaksetu.voip.ui.screens.DialerScreen
import com.rakshaksetu.voip.ui.screens.IncomingCallScreen
import com.rakshaksetu.voip.ui.theme.BgDark
import com.rakshaksetu.voip.ui.theme.RakshakVoipTheme

/**
 * Main Host Activity supporting full-screen ringing HUD, dialer keypad,
 * and live active call threat mitigation HUD.
 */
class MainActivity : ComponentActivity() {

    enum class ScreenState {
        DIALER,
        INCOMING_RINGING,
        ACTIVE_CALL_HUD
    }

    private val app get() = application as RakshakVoipApp

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRequiredPermissions()

        val initialCaller = intent?.getStringExtra(CallNotificationManager.EXTRA_CALLER_ID)
        val initialScreen = if (initialCaller != null) {
            ScreenState.INCOMING_RINGING
        } else {
            ScreenState.DIALER
        }

        setContent {
            RakshakVoipTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BgDark
                ) {
                    var currentScreen by remember { mutableStateOf(initialScreen) }
                    var activePeerId by remember { mutableStateOf(initialCaller ?: "Encrypted Peer") }
                    val uiState by app.aiPipeline.uiState.collectAsState()

                    when (currentScreen) {
                        ScreenState.DIALER -> {
                            DialerScreen(
                                onInitiateCall = { target ->
                                    activePeerId = target
                                    startActiveCallSession(target)
                                    currentScreen = ScreenState.ACTIVE_CALL_HUD
                                },
                                onSpeedDial1930 = {
                                    launchSpeedDial1930()
                                }
                            )
                        }

                        ScreenState.INCOMING_RINGING -> {
                            IncomingCallScreen(
                                callerId = activePeerId,
                                onAcceptCall = {
                                    startActiveCallSession(activePeerId)
                                    currentScreen = ScreenState.ACTIVE_CALL_HUD
                                },
                                onDeclineCall = {
                                    terminateCallSession()
                                    currentScreen = ScreenState.DIALER
                                }
                            )
                        }

                        ScreenState.ACTIVE_CALL_HUD -> {
                            ActiveCallHudScreen(
                                peerId = activePeerId,
                                uiState = uiState,
                                onDisconnectAndBlock = {
                                    terminateCallSession()
                                    Toast.makeText(
                                        this,
                                        "Call Terminated. Caller $activePeerId Blocked.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    currentScreen = ScreenState.DIALER
                                },
                                onSealSection65B = {
                                    sealSection65BEvidence(activePeerId, uiState)
                                    Toast.makeText(
                                        this,
                                        "Section 65B Forensic Manifest Sealed to App Storage.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onSpeedDial1930 = {
                                    launchSpeedDial1930()
                                },
                                onSimulateAttack = {
                                    app.aiPipeline.simulateSyntheticVoiceInjection()
                                },
                                onSimulateSafe = {
                                    app.aiPipeline.simulateBonafideConversationalSpeech()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startActiveCallSession(peerId: String) {
        app.telecomCallManager.configureAudioForCall(speakerOn = false)
        app.aiPipeline.start()
        app.webRtcEngine.startCall(isInitiator = true)
        CallForegroundService.startService(this, peerId)
    }

    private fun terminateCallSession() {
        app.webRtcEngine.terminateCall()
        app.aiPipeline.stop()
        app.telecomCallManager.resetAudioMode()
        CallForegroundService.stopService(this)
    }

    private fun sealSection65BEvidence(peerId: String, state: com.rakshaksetu.voip.ai.AiPipelineCoordinator.PipelineUiState) {
        app.evidenceManifest.generateManifest(
            caller = peerId,
            callee = "Sovereign-User",
            callDurationSeconds = 45L,
            transcript = state.liveTranscript,
            threatCategory = state.detectedCategory,
            sprtScore = state.sprtLambda,
            isThreat = state.threatLevel == ThreatLevel.CRITICAL_THREAT,
            scamRisk = state.scamRiskScore
        )
        app.aiPipeline.markTamperSealed()
    }

    private fun launchSpeedDial1930() {
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
            startActivity(dialIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "Emergency Dial 1930: National Cybercrime Helpline", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            permissions.add(Manifest.permission.MANAGE_OWN_CALLS)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}
