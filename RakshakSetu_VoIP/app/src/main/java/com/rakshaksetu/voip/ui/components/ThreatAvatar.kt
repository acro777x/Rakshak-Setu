package com.rakshaksetu.voip.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rakshaksetu.voip.ai.AiPipelineCoordinator.ThreatLevel
import com.rakshaksetu.voip.ui.theme.AmberWarn
import com.rakshaksetu.voip.ui.theme.CrimsonThreat
import com.rakshaksetu.voip.ui.theme.EmeraldSafe

/**
 * Pulsing threat avatar that morphs based on Wald SPRT threat state.
 */
@Composable
fun ThreatAvatar(
    threatLevel: ThreatLevel,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "AvatarPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (threatLevel == ThreatLevel.CRITICAL_THREAT) 1.25f else 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (threatLevel == ThreatLevel.CRITICAL_THREAT) 600 else 1200,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingAlpha"
    )

    val primaryColor by animateColorAsState(
        targetValue = when (threatLevel) {
            ThreatLevel.SAFE -> EmeraldSafe
            ThreatLevel.EVALUATING -> AmberWarn
            ThreatLevel.CRITICAL_THREAT -> CrimsonThreat
        },
        animationSpec = tween(durationMillis = 300),
        label = "AvatarColor"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(110.dp)
    ) {
        // Outer pulsing energy ring
        Box(
            modifier = Modifier
                .size(105.dp)
                .scale(pulseScale)
                .background(primaryColor.copy(alpha = ringAlpha * 0.4f), CircleShape)
                .border(1.5.dp, primaryColor.copy(alpha = ringAlpha), CircleShape)
        )

        // Middle aura ring
        Box(
            modifier = Modifier
                .size(85.dp)
                .background(primaryColor.copy(alpha = 0.2f), CircleShape)
                .border(2.dp, primaryColor.copy(alpha = 0.6f), CircleShape)
        )

        // Center glass core
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(68.dp)
                .background(Color(0xFF141C24), CircleShape)
                .border(2.5.dp, primaryColor, CircleShape)
        ) {
            Icon(
                imageVector = if (threatLevel == ThreatLevel.CRITICAL_THREAT) {
                    Icons.Default.Warning
                } else {
                    Icons.Default.Security
                },
                contentDescription = "Threat Status",
                tint = primaryColor,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
