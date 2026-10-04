package com.rakshaksetu.app.ui.voip

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.app.ui.components.BottomNavBar
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*

/**
 * Utility for formatting Indian mobile numbers (+91) with standard grouping.
 */
object IndianNumberFormatter {
    fun format(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        if (trimmed.startsWith("+91")) {
            val rest = trimmed.substring(3).filter { it.isDigit() }
            return when {
                rest.isEmpty() -> "+91 "
                rest.length <= 5 -> "+91 $rest"
                rest.length <= 10 -> "+91 ${rest.take(5)} ${rest.drop(5)}"
                else -> "+91 ${rest.take(5)} ${rest.substring(5, 10)} ${rest.drop(10)}"
            }
        }

        val pureDigits = trimmed.filter { it.isDigit() }
        return when {
            pureDigits.length == 10 -> "+91 ${pureDigits.take(5)} ${pureDigits.drop(5)}"
            pureDigits.length in 6..9 -> "${pureDigits.take(5)} ${pureDigits.drop(5)}"
            else -> trimmed
        }
    }

    fun toDialable(input: String): String {
        val cleaned = input.filter { it.isDigit() || it == '+' }
        if (cleaned.startsWith("+91")) return cleaned
        val pure = cleaned.filter { it.isDigit() }
        if (pure.length == 10) return "+91$pure"
        return cleaned
    }
}

/**
 * Clean white card dialer strictly adhering to RakshakSetuTheme:
 * - Material 3
 * - RakshakSetuBlue (#1565C0) brand accents
 * - SurfaceWhite (#FFFFFF) card surface
 * - BackgroundLight (#F8FAFF) background
 * - Indian number formatting (+91)
 * - Circular keypad buttons with tactile haptics and letters
 * - Backspace with long-press clear
 * - 1930 Cyber Helpline speed dial
 */
@Composable
fun VoipDialerScreen(
    onInitiateCall: (destination: String) -> Unit,
    onSpeedDial1930: () -> Unit,
    localDeviceId: String = "",
    onSetLocalDeviceId: ((String) -> Unit)? = null,
    onNavigate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var rawDialString by remember { mutableStateOf("") }
    var identityDraft by remember { mutableStateOf("") }
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

    Scaffold(
        bottomBar = {
            if (onNavigate != null) {
                BottomNavBar(
                    currentRoute = Screen.SecureLine.route,
                    onNavigate = onNavigate
                )
            }
        },
        containerColor = BackgroundLight
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundLight)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP SECTION: Sovereign Badge & Display Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Sovereign Encryption Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .background(SafeGreenLight, RoundedCornerShape(20.dp))
                        .border(1.dp, SafeGreen.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
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
                        text = "SOVEREIGN DTLS-SRTP ENCRYPTED",
                        color = SafeGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Clean White Card Display for Dial String
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
                            .padding(vertical = 16.dp, horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val formattedDisplay = IndianNumberFormatter.format(rawDialString)

                        Text(
                            text = if (formattedDisplay.isEmpty()) "Enter Indian Mobile (+91)" else formattedDisplay,
                            color = if (formattedDisplay.isEmpty()) TextSecondary.copy(alpha = 0.6f) else TextPrimary,
                            fontSize = if (formattedDisplay.length > 14) 24.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.SansSerif,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Userspace RAM intercept active · In-flight AI ready",
                            color = RakshakSetuBlue,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // This device's own Secure Line identity. Peers address each other by
                // phone number, so the number registered here is the one the other
                // device must dial. Without a visible, settable identity neither
                // device can be reached.
                if (localDeviceId.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = RakshakSetuBlueLight.copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, RakshakSetuBlue.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "THIS DEVICE'S SECURE LINE ID",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = localDeviceId,
                                color = RakshakSetuBlue,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Share this number so the other device can dial you",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )

                            if (onSetLocalDeviceId != null) {
                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedTextField(
                                    value = identityDraft,
                                    onValueChange = { identityDraft = it.filter { ch -> ch.isDigit() || ch == '+' }.take(15) },
                                    singleLine = true,
                                    label = { Text("My number (+91 98765 43210)") },
                                    placeholder = { Text(localDeviceId, fontSize = 11.sp) },
                                    textStyle = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedButton(
                                    onClick = {
                                        val entered = identityDraft.trim()
                                        if (entered.isNotEmpty()) {
                                            onSetLocalDeviceId(entered)
                                            identityDraft = ""
                                        }
                                    },
                                    enabled = identityDraft.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RakshakSetuBlue),
                                    border = BorderStroke(1.dp, RakshakSetuBlue.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "Save number",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CENTER SECTION: Circular Keypad Grid (3 columns x 4 rows)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0 until 3) {
                                val key = keys[row * 3 + col]
                                CircularKeypadButton(
                                    digit = key.first,
                                    subtext = key.second,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        rawDialString += key.first
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BOTTOM SECTION: Speed Dial 1930, Call Button, Backspace
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1930 Speed Dial Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(SuspiciousAmberLight)
                        .border(1.5.dp, SuspiciousAmber.copy(alpha = 0.6f), CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSpeedDial1930()
                        }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "1930 Cyber Helpline",
                            tint = SuspiciousAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "1930",
                            color = SuspiciousAmber,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Primary Call Button (RakshakSetuBlue with Phone icon)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(76.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(RakshakSetuBlue)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val target = if (rawDialString.isNotBlank()) {
                                IndianNumberFormatter.toDialable(rawDialString)
                            } else {
                                "+919876543210"
                            }
                            onInitiateCall(target)
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Call Secure Line",
                        tint = SurfaceWhite,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Backspace Button
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(if (rawDialString.isNotEmpty()) BorderColor.copy(alpha = 0.5f) else Color.Transparent)
                        .clickable(enabled = rawDialString.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (rawDialString.isNotEmpty()) {
                                rawDialString = rawDialString.dropLast(1)
                            }
                        }
                ) {
                    if (rawDialString.isNotEmpty()) {
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
}

/**
 * Circular keypad button adhering to RakshakSetuTheme.
 */
@Composable
private fun CircularKeypadButton(
    digit: String,
    subtext: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(SurfaceWhite)
            .border(1.dp, BorderColor, CircleShape)
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
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 28.sp
            )
            if (subtext.isNotBlank()) {
                Text(
                    text = subtext,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.1.sp
                )
            }
        }
    }
}

// Canonical typealias for Prototype 2 compatibility
@Composable
fun DialerScreen(
    onInitiateCall: (destination: String) -> Unit,
    onSpeedDial1930: () -> Unit,
    modifier: Modifier = Modifier
) = VoipDialerScreen(onInitiateCall = onInitiateCall, onSpeedDial1930 = onSpeedDial1930, modifier = modifier)
