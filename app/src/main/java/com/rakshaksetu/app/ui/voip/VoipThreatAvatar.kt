package com.rakshaksetu.app.ui.voip

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.rakshaksetu.app.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.app.ui.theme.BlockedRed
import com.rakshaksetu.app.ui.theme.SafeGreen
import com.rakshaksetu.app.ui.theme.SurfaceWhite
import com.rakshaksetu.app.ui.theme.SuspiciousAmber

/**
 * Pulsing threat avatar reflecting real-time safety status:
 * - Safe: Calm breathing aura in SafeGreen (#2E7D32)
 * - Suspicious: Warning aura in SuspiciousAmber (#E65100)
 * - Crimson Threat: Urgent rapid pulse in BlockedRed (#C62828)
 *
 * Adheres to RakshakSetuTheme using a clean SurfaceWhite center core with high-contrast icon.
 */
@Composable
fun VoipThreatAvatar(
    threatLevel: ThreatLevel,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ThreatAvatarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = when (threatLevel) {
            ThreatLevel.CRITICAL_THREAT -> 1.25f
            ThreatLevel.EVALUATING -> 1.15f
            ThreatLevel.SAFE -> 1.08f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (threatLevel) {
                    ThreatLevel.CRITICAL_THREAT -> 550
                    ThreatLevel.EVALUATING -> 900
                    ThreatLevel.SAFE -> 1400
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (threatLevel) {
                    ThreatLevel.CRITICAL_THREAT -> 550
                    else -> 1100
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingAlpha"
    )

    val statusColor by animateColorAsState(
        targetValue = when (threatLevel) {
            ThreatLevel.SAFE -> SafeGreen
            ThreatLevel.EVALUATING -> SuspiciousAmber
            ThreatLevel.CRITICAL_THREAT -> BlockedRed
        },
        animationSpec = tween(durationMillis = 300),
        label = "StatusColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(116.dp)
    ) {
        // Outer pulsing energy ring
        Box(
            modifier = Modifier
                .size(112.dp)
                .scale(pulseScale)
                .background(statusColor.copy(alpha = ringAlpha * 0.35f), CircleShape)
                .border(1.5.dp, statusColor.copy(alpha = ringAlpha), CircleShape)
        )

        // Middle aura ring
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(statusColor.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, statusColor.copy(alpha = 0.5f), CircleShape)
        )

        // Center clean SurfaceWhite core with shadow
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(70.dp)
                .shadow(4.dp, CircleShape)
                .background(SurfaceWhite, CircleShape)
                .border(2.5.dp, statusColor, CircleShape)
        ) {
            Icon(
                imageVector = when (threatLevel) {
                    ThreatLevel.CRITICAL_THREAT -> Icons.Default.GppBad
                    ThreatLevel.EVALUATING -> Icons.Default.Warning
                    ThreatLevel.SAFE -> Icons.Default.Security
                },
                contentDescription = "Threat Level: $threatLevel",
                tint = statusColor,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}

// Canonical typealias for Prototype 2 compatibility
@Composable
fun ThreatAvatar(
    threatLevel: ThreatLevel,
    modifier: Modifier = Modifier
) = VoipThreatAvatar(threatLevel = threatLevel, modifier = modifier)
