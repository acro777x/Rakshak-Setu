package com.rakshaksetu.app.ui.screens

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
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
import com.rakshaksetu.app.model.DetectionStore
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
                    if (lastResult.isScam) onNavigate(Screen.RedAlert.route)
                }
            } else {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No active alerts. All calls and scans are currently normal.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            AlertNotifCard(
                "⚠ Suspicious Link Blocked",
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4A0000), Color(0xFFC62828), Color(0xFFE53935))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = SurfaceWhite)
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(SurfaceWhite.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Dangerous, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(64.dp))
            }

            Spacer(Modifier.height(20.dp))
            Surface(color = SurfaceWhite.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                Text("🚨 CONFIRMED HIGH RISK", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = SurfaceWhite, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Text("DANGER", style = MaterialTheme.typography.displayLarge, color = SurfaceWhite, fontWeight = FontWeight.ExtraBold)
            Text("High Risk Call Detected", style = MaterialTheme.typography.headlineSmall, color = SurfaceWhite.copy(alpha = 0.9f))
            Spacer(Modifier.height(8.dp))
            Text(lastResult?.phoneNumber ?: "+91 98765 43210", style = MaterialTheme.typography.headlineMedium, color = SurfaceWhite, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))
            Text(
                "On-device AASIST neural network detected synthetic voice cloning and extortion script patterns. Take immediate action.",
                style = MaterialTheme.typography.bodyMedium,
                color = SurfaceWhite.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(16.dp))
            Surface(color = SurfaceWhite.copy(alpha = 0.15f), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Analysis Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                    Text(
                        "Scam Type: ${lastResult?.scamType?.replace('_', ' ') ?: "AI Voice Clone Attack"}\nConfidence: ${((lastResult?.confidence ?: 0.85f) * 100).toInt()}%\nFlagged: ${lastResult?.flaggedSegments?.joinToString("; ") { it.text } ?: "Urgent money transfer demanded"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = SurfaceWhite.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Immediate Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SurfaceWhite)
                    listOf("Do NOT share OTP or PIN", "Block this number immediately", "Contact your bank directly", "File a cybercrime report on 1930").forEach { action ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SurfaceWhite.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            Text(action, style = MaterialTheme.typography.bodySmall, color = SurfaceWhite.copy(alpha = 0.85f))
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onNavigate(Screen.ReportStep1.route) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite)
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = BlockedRed, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report This Incident", color = BlockedRed, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, SurfaceWhite.copy(alpha = 0.5f))
            ) {
                Text("Dismiss (Dismiss Alert)", color = SurfaceWhite.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── YELLOW ALERT ──────────────────────────────────────────────
@Composable
fun YellowAlertScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF4A2800), Color(0xFFE65100), Color(0xFFFF6D00))))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = SurfaceWhite)
                }
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier.size(110.dp).clip(CircleShape).background(SurfaceWhite.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(60.dp))
            }
            Spacer(Modifier.height(16.dp))
            Surface(color = SurfaceWhite.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                Text("⚠ SUSPICIOUS ACTIVITY", modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = SurfaceWhite, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Text("WARNING", style = MaterialTheme.typography.displayLarge, color = SurfaceWhite, fontWeight = FontWeight.ExtraBold)
            Text("Suspicious Link Blocked", style = MaterialTheme.typography.headlineSmall, color = SurfaceWhite.copy(alpha = 0.9f))
            Spacer(Modifier.height(8.dp))
            Text("www.free-gift-reward.xyz", style = MaterialTheme.typography.titleLarge, color = SurfaceWhite, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(16.dp))
            Text(
                "This link was flagged for phishing patterns. Banking credentials or OTP theft suspected.",
                style = MaterialTheme.typography.bodyMedium,
                color = SurfaceWhite.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onNavigate(Screen.ReportStep1.route) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite)
            ) {
                Icon(Icons.Filled.Flag, contentDescription = null, tint = SuspiciousAmber, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Report This Incident", color = SuspiciousAmber, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, SurfaceWhite.copy(alpha = 0.5f))
            ) {
                Text("Dismiss", color = SurfaceWhite.copy(alpha = 0.7f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
