package com.rakshaksetu.voip.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.voip.ai.AiPipelineCoordinator.PipelineUiState
import com.rakshaksetu.voip.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.voip.ui.components.GlassmorphicCard
import com.rakshaksetu.voip.ui.components.SpectralWaveformView
import com.rakshaksetu.voip.ui.components.ThreatAvatar
import com.rakshaksetu.voip.ui.components.TranscriptView
import com.rakshaksetu.voip.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Dynamic in-call Threat HUD featuring real-time GPU spectral waveform,
 * pulsing morphing threat avatar, Wald SPRT accumulator bar, live streaming transcript,
 * and 1-tap sovereign defense countermeasures.
 */
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
) {
    val haptic = LocalHapticFeedback.current
    var callSeconds by remember { mutableStateOf(0) }

    // Call duration timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    val minutes = callSeconds / 60
    val seconds = callSeconds % 60
    val timeFormatted = "%02d:%02d".format(minutes, seconds)

    val hudHeaderColor by animateColorAsState(
        targetValue = when (uiState.threatLevel) {
            ThreatLevel.SAFE -> EmeraldSafe
            ThreatLevel.EVALUATING -> AmberWarn
            ThreatLevel.CRITICAL_THREAT -> CrimsonThreat
        },
        animationSpec = tween(300),
        label = "HudColor"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP: Encrypted badge & Peer Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(hudHeaderColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, hudHeaderColor.copy(alpha = 0.4f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
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
                        ThreatLevel.SAFE -> "SOVEREIGN ENCRYPTED CALL"
                        ThreatLevel.EVALUATING -> "ANALYZING ACOUSTICS..."
                        ThreatLevel.CRITICAL_THREAT -> "CRITICAL SCAM INTERCEPT"
                    },
                    color = hudHeaderColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = peerId,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = timeFormatted,
                color = TextSecondary,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CENTER: Threat Avatar & Waveform
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            ThreatAvatar(threatLevel = uiState.threatLevel)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = uiState.detectedCategory,
                color = hudHeaderColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Real-time GPU phase-shifted spectral waveform
            SpectralWaveformView(
                threatLevel = uiState.threatLevel,
                audioRms = uiState.audioRms,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // WALD SPRT ACCUMULATOR CARD
        GlassmorphicCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            backgroundColor = Color(0x14FFFFFF)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WALD SPRT ACCUMULATOR (A=5.288, B=-4.600)",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "%.1f%%".format(uiState.threatPercent),
                        color = hudHeaderColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Threat progress bar
                LinearProgressIndicator(
                    progress = { (uiState.threatPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = hudHeaderColor,
                    trackColor = Color(0x22FFFFFF)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Score Lambda: %.3f".format(uiState.sprtLambda),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Clone Prob: %.2f".format(uiState.cloneProbability),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // LIVE STREAMING TRANSCRIPT
        TranscriptView(
            transcriptText = uiState.liveTranscript,
            matchedSpans = uiState.matchedSpans,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // SECTION 65B SEAL STATUS BADGE (IF SEALED)
        if (uiState.isTamperSealed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x1A00E5FF), RoundedCornerShape(12.dp))
                    .border(1.dp, SovereignCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Verified,
                    contentDescription = null,
                    tint = SovereignCyan,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SECTION 65B EVIDENCE DOSSIER SEALED & VERIFIED",
                    color = SovereignCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 1-TAP DEFENSE COUNTERMEASURES BAR
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Disconnect & Block Scammer (Full width primary crimson)
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDisconnectAndBlock()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonThreat),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CallEnd,
                    contentDescription = null,
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DISCONNECT & BLOCK SCAMMER",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            // Row 2: Seal Section 65B Evidence + Speed Dial 1930
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSealSection65B()
                    },
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(SovereignCyan)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Gavel,
                        contentDescription = null,
                        tint = SovereignCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Seal 65B Evidence",
                        color = SovereignCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSpeedDial1930()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberWarn),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Speed Dial 1930",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Simulation / Testing quick triggers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSimulateAttack) {
                    Text(
                        text = "⚡ Inject Clone Attack",
                        color = CrimsonThreat.copy(alpha = 0.9f),
                        fontSize = 11.sp
                    )
                }

                TextButton(onClick = onSimulateSafe) {
                    Text(
                        text = "✓ Inject Safe Voice",
                        color = EmeraldSafe.copy(alpha = 0.9f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
