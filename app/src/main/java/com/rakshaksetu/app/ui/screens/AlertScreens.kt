package com.rakshaksetu.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.rakshaksetu.app.action.HelplineAction
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.ui.EvidenceActivity
import com.rakshaksetu.app.ui.GovtReportWebViewActivity
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.data.RiskStatus
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── ALERT CENTER ──────────────────────────────────────────────
@Composable
fun AlertCenterScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Alert Center", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("High-Priority Alerts", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            if (lastResult != null) {
                val timeStr = SimpleDateFormat("hh:mm a", Locale.US).format(Date(lastResult.callEndEpoch))
                AlertNotifCard(
                    title = if (lastResult.isScam) "🚨 High-Risk Call Detected" else "✅ Call Verified Safe",
                    body = "Call from ${lastResult.phoneNumber} — ${if (lastResult.isScam) "${lastResult.scamType?.replace('_', ' ')} (${(lastResult.confidence * 100).toInt()}% confidence)" else "No threats detected"}",
                    status = if (lastResult.isScam) RiskStatus.HIGH_RISK else RiskStatus.SAFE,
                    time = timeStr
                ) {
                    if (lastResult.isScam) {
                        if (lastResult.confidence >= 0.70f) {
                            onNavigate(Screen.RedAlert.route)
                        } else {
                            onNavigate(Screen.YellowAlert.route)
                        }
                    }
                }
            } else {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No active alerts. All calls and scans are currently normal.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            AlertNotifCard(
                "⚠️ Suspicious Link Blocked",
                "www.free-gift-reward.xyz — Phishing domain detected",
                RiskStatus.BLOCKED,
                "09:05 AM"
            ) { onNavigate(Screen.YellowAlert.route) }

            AlertNotifCard(
                "🛡️ Real-Time Shield Active",
                "On-device AASIST AI & Vosk Kaldi speech engine running in background",
                RiskStatus.SAFE,
                "System Live"
            ) {}
        }
    }
}

@Composable
fun AlertNotifCard(title: String, body: String, status: RiskStatus, time: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            val (bg, tint) = when (status) {
                RiskStatus.HIGH_RISK, RiskStatus.BLOCKED -> Pair(BlockedRedLight, BlockedRed)
                RiskStatus.SUSPICIOUS -> Pair(SuspiciousAmberLight, SuspiciousAmber)
                RiskStatus.SAFE -> Pair(SafeGreenLight, SafeGreen)
            }
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(bg), contentAlignment = Alignment.Center) {
                Icon(if (status == RiskStatus.SAFE) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text(time, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            if (status != RiskStatus.SAFE) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}

// ── RED ALERT ─────────────────────────────────────────────────
@Composable
fun RedAlertScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_scale"
    )

    val scamTitle = lastResult?.scamType?.replace('_', ' ')?.uppercase() ?: "SCAM DETECTED"
    val confPercent = ((lastResult?.confidence ?: 0.86f) * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF880E4F), Color(0xFFC62828), Color(0xFFD32F2F))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SurfaceWhite)
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(SurfaceWhite.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Dangerous, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(58.dp))
            }

            Spacer(Modifier.height(16.dp))
            Surface(color = SurfaceWhite.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                Text(
                    "🚨 CONFIRMED HIGH RISK ($confPercent%)",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = SurfaceWhite,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "DANGER",
                style = MaterialTheme.typography.displayLarge,
                color = SurfaceWhite,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Text(
                scamTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = SurfaceWhite.copy(alpha = 0.95f),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                lastResult?.phoneNumber ?: "+91 98765 43210",
                style = MaterialTheme.typography.headlineMedium,
                color = SurfaceWhite,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "On-device AASIST neural network & Kaldi speech engine detected synthetic voice cloning and urgent financial extortion patterns.",
                style = MaterialTheme.typography.bodyMedium,
                color = SurfaceWhite.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(16.dp))
            // Analysis Breakdown with PROMINENT HIGHLIGHTED FLAGGED SPEECH
            Surface(
                color = SurfaceWhite.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SurfaceWhite.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Forensic Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        Surface(
                            color = SurfaceWhite.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "$confPercent% Match",
                                color = SurfaceWhite,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // HIGHLIGHTED FLAGGED PHRASES BOX
                    Text(
                        "🎙️ Flagged Conversation Phrases:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFCDD2)
                    )

                    if (lastResult?.flaggedSegments?.isNotEmpty() == true) {
                        lastResult.flaggedSegments.forEach { segment ->
                            Surface(
                                color = Color(0x33000000),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0x66FF8A80)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "“${segment.text}”",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF8A80),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFFF8A80),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${(segment.similarity * 100).toInt()}% match",
                                            color = Color(0xFF880E4F),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0x33000000),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0x66FF8A80)),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = "“Aapka SBI account KYC expire ho gaya hai; Aaj raat 9 baje tak OTP share karo”",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF8A80),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Immediate Safety Actions:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                    listOf(
                        "Do NOT share OTP, UPI PIN, or bank passwords",
                        "Disconnect & block this suspect caller immediately",
                        "Contact your bank directly to secure your account",
                        "File an official Cybercrime report on 1930 / NCRP"
                    ).forEach { action ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SurfaceWhite.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                            Text(action, style = MaterialTheme.typography.bodySmall, color = SurfaceWhite.copy(alpha = 0.9f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            // CLEAN PROFESSIONAL PRIMARY BUTTON (NO AI EMOJIS)
            Button(
                onClick = {
                    val intent = Intent(context, EvidenceActivity::class.java).apply {
                        putExtra(ScamAlertManager.EXTRA_CALL_ID, lastResult?.callId ?: "unknown")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = BlockedRed, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("View Evidence Dossier", color = BlockedRed, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        try {
                            HelplineAction.dial1930(context)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite.copy(alpha = 0.25f))
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Call 1930", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(context, GovtReportWebViewActivity::class.java).apply {
                                putExtra(GovtReportWebViewActivity.EXTRA_CALL_ID, lastResult?.callId ?: "")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite.copy(alpha = 0.25f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("NCRP Report", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, SurfaceWhite.copy(alpha = 0.5f))
            ) {
                Text("Dismiss Alert (False Positive)", color = SurfaceWhite.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── YELLOW ALERT ──────────────────────────────────────────────
@Composable
fun YellowAlertScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }

    val scamTitle = lastResult?.scamType?.replace('_', ' ')?.uppercase() ?: "SUSPICIOUS ACTIVITY"
    val confPercent = ((lastResult?.confidence ?: 0.65f) * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4A2800), Color(0xFFE65100), Color(0xFFFF6D00))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SurfaceWhite)
                }
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(SurfaceWhite.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(58.dp))
            }

            Spacer(Modifier.height(16.dp))
            Surface(color = SurfaceWhite.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                Text(
                    "⚠️ SUSPICIOUS PATTERN DETECTED ($confPercent%)",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = SurfaceWhite,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("WARNING", style = MaterialTheme.typography.displayLarge, color = SurfaceWhite, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text(scamTitle, style = MaterialTheme.typography.headlineSmall, color = SurfaceWhite.copy(alpha = 0.95f), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(lastResult?.phoneNumber ?: "+91 98765 43210", style = MaterialTheme.typography.headlineMedium, color = SurfaceWhite, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(14.dp))
            Text(
                "This caller or link shows suspicious acoustic and conversational urgency patterns. Remain cautious and verify independently.",
                style = MaterialTheme.typography.bodyMedium,
                color = SurfaceWhite.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(16.dp))
            // Analysis Breakdown with PROMINENT HIGHLIGHTED FLAGGED SPEECH
            Surface(
                color = SurfaceWhite.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SurfaceWhite.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Forensic Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        Surface(
                            color = SurfaceWhite.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                "$confPercent% Score",
                                color = SurfaceWhite,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // HIGHLIGHTED FLAGGED PHRASES BOX
                    Text(
                        "🎙️ Flagged Conversation Phrases:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFE082)
                    )

                    if (lastResult?.flaggedSegments?.isNotEmpty() == true) {
                        lastResult.flaggedSegments.forEach { segment ->
                            Surface(
                                color = Color(0x33000000),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0x66FFE082)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "“${segment.text}”",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFE082),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFFFFE082),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "${(segment.similarity * 100).toInt()}% match",
                                            color = Color(0xFF4A2800),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Surface(
                            color = Color(0x33000000),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color(0x66FFE082)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "“Suspicious phishing pattern and urgent demand detected”",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFE082),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("What to verify:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                    listOf(
                        "Do not enter personal or banking credentials",
                        "Verify with the sender through an official phone number",
                        "Do NOT share one-time passwords (OTP)",
                        "If in doubt, report on National Cybercrime Portal"
                    ).forEach { action ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = SurfaceWhite.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                            Text(action, style = MaterialTheme.typography.bodySmall, color = SurfaceWhite.copy(alpha = 0.9f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            // CLEAN PROFESSIONAL PRIMARY BUTTON (NO AI EMOJIS)
            Button(
                onClick = {
                    val intent = Intent(context, EvidenceActivity::class.java).apply {
                        putExtra(ScamAlertManager.EXTRA_CALL_ID, lastResult?.callId ?: "unknown")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, tint = SuspiciousAmber, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("View Evidence Dossier", color = SuspiciousAmber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        try {
                            HelplineAction.dial1930(context)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite.copy(alpha = 0.25f))
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Call 1930", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        try {
                            val intent = Intent(context, GovtReportWebViewActivity::class.java).apply {
                                putExtra(GovtReportWebViewActivity.EXTRA_CALL_ID, lastResult?.callId ?: "")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite.copy(alpha = 0.25f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Report Portal", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, SurfaceWhite.copy(alpha = 0.5f))
            ) {
                Text("Dismiss (Not Suspicious)", color = SurfaceWhite.copy(alpha = 0.85f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
