package com.rakshaksetu.app.ui.voip

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.app.ai.AiPipelineCoordinator
import com.rakshaksetu.app.ai.AiPipelineCoordinator.PipelineUiState
import com.rakshaksetu.app.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.app.forensics.Section65BEvidenceManager
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Reskinned Active Call Threat HUD adhering strictly to RakshakSetuTheme:
 * - Material 3
 * - BackgroundLight (#F8FAFF)
 * - Clean white cards with crisp blue accents
 * - Live GPU spectral waveform gliding in Electric Blue / RakshakSetuBlue, turning Crimson on alert
 * - Pulsing Threat Avatar reflecting safety status (SafeGreen -> SuspiciousAmber -> BlockedRed)
 * - Wald SPRT Accumulator Card
 * - Simulated Authorization Card with 10.0s approval countdown (freezes & auto-severs on clone attack)
 * - Streaming transcript card with live highlighting of scam keywords
 * - 1-Tap defense buttons: "Disconnect & Block Scammer", "Seal Section 65B Evidence", "Speed Dial 1930"
 */
@Composable
fun VoipActiveCallHudScreen(
    peerId: String = "+91 98765 43210",
    coordinator: AiPipelineCoordinator? = null,
    onBack: (() -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Active AI Pipeline Coordinator
    val activeCoordinator = remember(coordinator) {
        coordinator ?: AiPipelineCoordinator(context).apply { start() }
    }

    val uiState by activeCoordinator.uiState.collectAsState()

    DisposableEffect(activeCoordinator) {
        onDispose {
            if (coordinator == null) {
                activeCoordinator.stop()
            }
        }
    }

    VoipActiveCallHudContent(
        peerId = peerId,
        uiState = uiState,
        onDisconnectAndBlock = {
            activeCoordinator.stop()
            onBack?.invoke()
        },
        onSealSection65B = {
            val evidenceManager = Section65BEvidenceManager(context)
            evidenceManager.generateManifest(
                caller = peerId,
                callee = "+919876543210",
                callDurationSeconds = 12L,
                transcript = uiState.liveTranscript,
                threatCategory = uiState.detectedCategory,
                sprtScore = uiState.sprtLambda,
                isThreat = uiState.threatLevel == ThreatLevel.CRITICAL_THREAT,
                scamRisk = uiState.scamRiskScore
            )
            activeCoordinator.markTamperSealed()
        },
        onSpeedDial1930 = {
            activeCoordinator.stop()
            onNavigate?.invoke("https://cybercrime.gov.in")
        },
        onSimulateAttack = {
            activeCoordinator.simulateSyntheticVoiceInjection()
        },
        onSimulateSafe = {
            activeCoordinator.simulateBonafideConversationalSpeech()
        },
        modifier = modifier
    )
}

/**
 * Pure composable rendering the HUD given state and action lambdas.
 */
@Composable
fun VoipActiveCallHudContent(
    peerId: String,
    uiState: PipelineUiState,
    onDisconnectAndBlock: () -> Unit,
    onSealSection65B: () -> Unit,
    onSpeedDial1930: () -> Unit,
    onSimulateAttack: () -> Unit,
    onSimulateSafe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var callSeconds by remember { mutableIntStateOf(0) }

    // Call duration timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    val minutes = callSeconds / 60
    val seconds = callSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    val hudHeaderColor by animateColorAsState(
        targetValue = when (uiState.threatLevel) {
            ThreatLevel.SAFE -> SafeGreen
            ThreatLevel.EVALUATING -> SuspiciousAmber
            ThreatLevel.CRITICAL_THREAT -> BlockedRed
        },
        animationSpec = tween(300),
        label = "HudColor"
    )

    val hudHeaderBg by animateColorAsState(
        targetValue = when (uiState.threatLevel) {
            ThreatLevel.SAFE -> SafeGreenLight
            ThreatLevel.EVALUATING -> SuspiciousAmberLight
            ThreatLevel.CRITICAL_THREAT -> BlockedRedLight
        },
        animationSpec = tween(300),
        label = "HudBg"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP SECTION: Header with Encrypted Badge and Timer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(hudHeaderBg, RoundedCornerShape(20.dp))
                    .border(1.dp, hudHeaderColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = hudHeaderColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (uiState.threatLevel) {
                        ThreatLevel.SAFE -> "SOVEREIGN ENCRYPTED CALL - SECURE"
                        ThreatLevel.EVALUATING -> "ANALYZING ACOUSTIC SPECTRA..."
                        ThreatLevel.CRITICAL_THREAT -> "CRIMSON THREAT - VOICE CLONE DETECTED"
                    },
                    color = hudHeaderColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = IndianNumberFormatter.format(peerId),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // CENTER SECTION: Threat Avatar & Waveform Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = BorderStroke(1.dp, BorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Pulsing Threat Avatar
                VoipThreatAvatar(threatLevel = uiState.threatLevel)

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = uiState.detectedCategory,
                    color = hudHeaderColor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Live GPU Waveform gliding in RakshakSetuBlue (Safe) or BlockedRed (Threat)
                VoipWaveformView(
                    threatLevel = uiState.threatLevel,
                    audioRms = uiState.audioRms,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                        .background(BackgroundLight, RoundedCornerShape(10.dp))
                        .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // WALD SPRT ACCUMULATOR CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
            border = BorderStroke(1.dp, BorderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WALD SPRT ACCUMULATOR (A = +5.288, B = -4.600)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%% Threat", uiState.threatPercent),
                        color = hudHeaderColor,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Threat progress indicator
                LinearProgressIndicator(
                    progress = { (uiState.threatPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = hudHeaderColor,
                    trackColor = BorderColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = String.format(Locale.US, "Log-Likelihood Λ: %.3f", uiState.sprtLambda),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "Clone Prob: %.2f", uiState.cloneProbability),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SIMULATED AUTHORIZATION CARD (10.0-second approval countdown)
        SimulatedAuthorizationCard(
            threatLevel = uiState.threatLevel,
            sprtLambda = uiState.sprtLambda,
            cloneProbability = uiState.cloneProbability,
            onEmergencyCallSever = onDisconnectAndBlock,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // LIVE STREAMING TRANSCRIPT CARD
        VoipTranscriptView(
            transcriptText = uiState.liveTranscript,
            matchedSpans = uiState.matchedSpans,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // SECTION 65B EVIDENCE DOSSIER SEALED BADGE (IF SEALED)
        if (uiState.isTamperSealed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SafeGreenLight, RoundedCornerShape(12.dp))
                    .border(1.dp, SafeGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = SafeGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SECTION 65B EVIDENCE DOSSIER SEALED & COURT-ADMISSIBLE",
                    color = SafeGreen,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 1-TAP SOVEREIGN DEFENSE COUNTERMEASURES
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Primary Killswitch: "Disconnect & Block Scammer"
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDisconnectAndBlock()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlockedRed),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = null,
                    tint = SurfaceWhite
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISCONNECT & BLOCK SCAMMER",
                    color = SurfaceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Secondary Actions: Seal Section 65B Evidence + Speed Dial 1930
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSealSection65B()
                    },
                    border = BorderStroke(1.5.dp, RakshakSetuBlue),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = RakshakSetuBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Seal 65B Evidence",
                        color = RakshakSetuBlue,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSpeedDial1930()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuspiciousAmber),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Speed Dial 1930",
                        color = SurfaceWhite,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Demo & Test Quick Injections
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSimulateAttack) {
                    Text(
                        text = "⚡ Inject Clone Attack",
                        color = BlockedRed,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(onClick = onSimulateSafe) {
                    Text(
                        text = "✓ Inject Safe Voice",
                        color = SafeGreen,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// Canonical typealiases for Prototype 2 compatibility
@Composable
fun ActiveCallHudScreen(
    peerId: String,
    uiState: PipelineUiState,
    onDisconnectAndBlock: () -> Unit,
    onSealSection65B: () -> Unit,
    onSpeedDial1930: () -> Unit,
    onSimulateAttack: () -> Unit,
    onSimulateSafe: () -> Unit,
    modifier: Modifier = Modifier
) = VoipActiveCallHudContent(
    peerId = peerId,
    uiState = uiState,
    onDisconnectAndBlock = onDisconnectAndBlock,
    onSealSection65B = onSealSection65B,
    onSpeedDial1930 = onSpeedDial1930,
    onSimulateAttack = onSimulateAttack,
    onSimulateSafe = onSimulateSafe,
    modifier = modifier
)

@Composable
fun ThreatCallHudScreen(
    peerId: String,
    uiState: PipelineUiState,
    onDisconnectAndBlock: () -> Unit,
    onSealSection65B: () -> Unit,
    onSpeedDial1930: () -> Unit,
    onSimulateAttack: () -> Unit,
    onSimulateSafe: () -> Unit,
    modifier: Modifier = Modifier
) = VoipActiveCallHudContent(
    peerId = peerId,
    uiState = uiState,
    onDisconnectAndBlock = onDisconnectAndBlock,
    onSealSection65B = onSealSection65B,
    onSpeedDial1930 = onSpeedDial1930,
    onSimulateAttack = onSimulateAttack,
    onSimulateSafe = onSimulateSafe,
    modifier = modifier
)
