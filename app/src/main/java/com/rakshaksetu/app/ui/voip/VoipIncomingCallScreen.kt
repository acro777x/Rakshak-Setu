package com.rakshaksetu.app.ui.voip

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.app.ui.theme.*

/**
 * Clean full-screen incoming call UI adhering to RakshakSetuTheme:
 * - Material 3
 * - BackgroundLight (#F8FAFF)
 * - Clean white card surfaces
 * - Caller ID display ("DCP Cyber Crime" or phone number)
 * - Accept button in SafeGreen (#2E7D32)
 * - Decline button in BlockedRed (#C62828)
 * - In-flight RAM intercept armed advisory
 */
@Composable
fun VoipIncomingCallScreen(
    callerId: String,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "RingingPulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "RingingPulseScale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP SECTION: Sovereign Call Status
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 28.dp)
        ) {
            // Sovereign DTLS-SRTP Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(SafeGreenLight, RoundedCornerShape(20.dp))
                    .border(1.dp, SafeGreen.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SafeGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "INCOMING DTLS-SRTP CALL",
                    color = SafeGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Pulsing Caller Avatar
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(130.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(126.dp)
                        .scale(pulseScale)
                        .background(RakshakSetuBlue.copy(alpha = 0.12f), CircleShape)
                        .border(2.dp, RakshakSetuBlue.copy(alpha = 0.35f), CircleShape)
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(4.dp, CircleShape)
                        .background(SurfaceWhite, CircleShape)
                        .border(2.5.dp, RakshakSetuBlue, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Caller",
                        tint = RakshakSetuBlue,
                        modifier = Modifier.size(44.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Caller Identifier & Phone Number
            val formattedCallerId = IndianNumberFormatter.format(callerId)

            Text(
                text = formattedCallerId.ifEmpty { "Unknown Caller" },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Ringing · In-flight RAM intercept ready...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Sovereign Shield Armed Advisory Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = RakshakSetuBlue,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Rakshak Setu Sovereign Shield armed: Real-time neural voice clone analysis and Section 65B forensic logging enabled.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // BOTTOM SECTION: Accept / Decline Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline (BlockedRed)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(BlockedRed)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDeclineCall()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Decline Call",
                        tint = SurfaceWhite,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Decline",
                    style = MaterialTheme.typography.titleSmall,
                    color = BlockedRed,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Accept (SafeGreen)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(SafeGreen)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAcceptCall()
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Accept Call",
                        tint = SurfaceWhite,
                        modifier = Modifier.size(34.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Accept",
                    style = MaterialTheme.typography.titleSmall,
                    color = SafeGreen,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Canonical typealias for Prototype 2 compatibility
@Composable
fun IncomingCallScreen(
    callerId: String,
    onAcceptCall: () -> Unit,
    onDeclineCall: () -> Unit,
    modifier: Modifier = Modifier
) = VoipIncomingCallScreen(callerId = callerId, onAcceptCall = onAcceptCall, onDeclineCall = onDeclineCall, modifier = modifier)
