package com.rakshaksetu.voip.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.voip.ui.components.GlassmorphicCard
import com.rakshaksetu.voip.ui.theme.*

/**
 * iOS-grade round glassmorphic dialer keypad with tactile haptics,
 * sovereign encrypted status indicator, and instant 1930 Cyber Helpline speed dial.
 */
@Composable
fun DialerScreen(
    onInitiateCall: (destination: String) -> Unit,
    onSpeedDial1930: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dialString by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val keys = listOf(
        Triple("1", "", ""),
        Triple("2", "A B C", ""),
        Triple("3", "D E F", ""),
        Triple("4", "G H I", ""),
        Triple("5", "J K L", ""),
        Triple("6", "M N O", ""),
        Triple("7", "P Q R S", ""),
        Triple("8", "T U V", ""),
        Triple("9", "W X Y Z", ""),
        Triple("*", "", ""),
        Triple("0", "+", ""),
        Triple("#", "", "")
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Sovereign Header & Security Badge
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .background(Color(0x1400E676), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x3300E676), RoundedCornerShape(20.dp))
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
                    text = "SOVEREIGN DTLS-SRTP ENCRYPTED",
                    color = EmeraldSafe,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Dial display
            Text(
                text = if (dialString.isEmpty()) "Enter VoIP Identifier" else dialString,
                color = if (dialString.isEmpty()) TextMuted else TextPrimary,
                fontSize = if (dialString.length > 10) 28.sp else 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }

        // Keypad Grid (3 columns x 4 rows)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (col in 0 until 3) {
                        val key = keys[row * 3 + col]
                        RoundKeypadButton(
                            digit = key.first,
                            subtext = key.second,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                dialString += key.first
                            }
                        )
                    }
                }
            }
        }

        // Bottom Action Bar: 1930 Speed Dial, Call Button, Backspace
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Dial 1930
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AmberWarn.copy(alpha = 0.15f))
                    .border(1.5.dp, AmberWarn.copy(alpha = 0.5f), CircleShape)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSpeedDial1930()
                    }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "1930 Helpline",
                        tint = AmberWarn,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "1930",
                        color = AmberWarn,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Emerald Call Initiate Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(EmeraldSafe)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val target = if (dialString.isNotBlank()) dialString else "Peer-Sovereign-1"
                        onInitiateCall(target)
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "Call",
                    tint = Color.Black,
                    modifier = Modifier.size(34.dp)
                )
            }

            // Backspace Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (dialString.isNotEmpty()) KeypadButtonBg else Color.Transparent)
                    .clickable(enabled = dialString.isNotEmpty()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (dialString.isNotEmpty()) {
                            dialString = dialString.dropLast(1)
                        }
                    }
            ) {
                if (dialString.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Backspace,
                        contentDescription = "Backspace",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * iOS-style circular keypad button with large number and subtle alphabet subtext.
 */
@Composable
private fun RoundKeypadButton(
    digit: String,
    subtext: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(KeypadButtonBg)
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) {
                onClick()
            }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 30.sp
            )
            if (subtext.isNotBlank()) {
                Text(
                    text = subtext,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}
