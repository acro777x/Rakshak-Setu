package com.rakshaksetu.voip.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.voip.ui.theme.*

/**
 * Full-screen glassmorphic ringing HUD with accept and decline actions.
 */
@Composable
fun IncomingCallScreen(
    callerId: String,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "RingingPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color(0x1400E676), CircleShape)
                    .border(1.dp, Color(0x3300E676), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Encrypted",
                    tint = EmeraldSafe,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "INCOMING DTLS-SRTP CALL",
                    color = EmeraldSafe,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Pulsing Caller Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(116.dp)
                        .scale(pulseScale)
                        .background(EmeraldSafe.copy(alpha = 0.15f), CircleShape)
                        .border(2.dp, EmeraldSafe.copy(alpha = 0.4f), CircleShape)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(80.dp)
                        .background(BgDarkElevated, CircleShape)
                        .border(2.dp, EmeraldSafe, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Caller",
                        tint = EmeraldSafe,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = callerId,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Ringing (In-flight RAM intercept ready)...",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        // Bottom Accept / Decline Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline (Crimson)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(CrimsonThreat)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeclineCall()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline Call",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Decline", color = TextSecondary, fontSize = 12.sp)
            }

            // Accept (Emerald)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(EmeraldSafe)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAcceptCall()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Accept Call",
                        tint = Color.Black,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Accept", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}
