package com.rakshaksetu.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.rakshaksetu.app.service.BatteryOptimizationHelper
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.pipeline.ModelDownloadManager
import com.rakshaksetu.app.service.AnalysisService
import com.rakshaksetu.app.ui.GovtReportWebViewActivity
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.data.*
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    val consentStore = remember { ConsentStore(context) }
    var isShieldActive by remember { mutableStateOf(consentStore.isShieldActive) }
    var lastResult by remember { mutableStateOf<DetectionResult?>(DetectionStore.getLastResult(context)) }

    // Check Android Runtime Permissions
    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPhonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        scope.launch {
            snackbarHostState.showSnackbar("Permissions updated!")
        }
    }

    // AI Model States
    var asrReady by remember { mutableStateOf(ModelDownloadManager.isAsrModelReady(context)) }
    var embedReady by remember { mutableStateOf(ModelDownloadManager.isEmbeddingModelReady(context)) }
    var deepfakeReady by remember { mutableStateOf(ModelDownloadManager.isDeepfakeModelReady(context)) }
    var modelProgressText by remember { mutableStateOf("") }
    var modelBusy by remember { mutableStateOf(false) }

    // Compute live stats from DetectionStore
    val safeCount = if (lastResult != null && !lastResult!!.isScam) 1 else 0
    val suspiciousCount = if (lastResult != null && lastResult!!.isScam && lastResult!!.confidence < 0.7f) 1 else 0
    val blockedCount = if (lastResult != null && lastResult!!.isScam && lastResult!!.confidence >= 0.7f) 1 else 0
    val totalScanned = if (lastResult != null) 1 else 0

    fun triggerScenario(res: DetectionResult, label: String) {
        try {
            DetectionStore.saveLastResult(context, res)
            lastResult = res
            ScamAlertManager(context).showScamAlert(res)
            val serviceIntent = Intent(context, AnalysisService::class.java).apply {
                putExtra(AnalysisService.EXTRA_CALL_ID, res.callId)
                putExtra(AnalysisService.EXTRA_PHONE_NUMBER, res.phoneNumber)
                putExtra(AnalysisService.EXTRA_IS_SIMULATION, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            scope.launch { snackbarHostState.showSnackbar("🚨 Simulation triggered: $label") }
        } catch (e: Exception) {
            scope.launch { snackbarHostState.showSnackbar("Error: ${e.message}") }
        }
    }

    Scaffold(
        topBar = {
            RakshakSetuTopBar(
                title = "Rakshak Setu",
                onMenuClick = {},
                onNotificationClick = { onNavigate(Screen.AlertCenter.route) },
                onProfileClick = { onNavigate(Screen.Profile.route) }
            )
        },
        bottomBar = { BottomNavBar(currentRoute = Screen.Dashboard.route, onNavigate = onNavigate) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── SHIELD ACTIVE TOGGLE CARD ───────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isShieldActive) Color(0xFF1B381E) else Color(0xFF382A1B)
                ),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (isShieldActive) Icons.Filled.Shield else Icons.Filled.ShieldMoon,
                                contentDescription = null,
                                tint = if (isShieldActive) Color(0xFF81C784) else Color(0xFFFFB74D),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isShieldActive) "SHIELD ACTIVE" else "SHIELD PAUSED",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isShieldActive) Color(0xFF81C784) else Color(0xFFFFB74D)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (isShieldActive) "On-device AI actively detecting scam & voice clone attacks" else "Monitoring paused. No call audio is analyzed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SurfaceWhite.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = isShieldActive,
                        onCheckedChange = { active ->
                            isShieldActive = active
                            consentStore.isShieldActive = active
                            if (active) {
                                com.rakshaksetu.app.service.RakshakShieldService.start(context)
                            } else {
                                com.rakshaksetu.app.service.RakshakShieldService.stop(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SurfaceWhite,
                            checkedTrackColor = Color(0xFF2E7D32),
                            uncheckedThumbColor = SurfaceWhite,
                            uncheckedTrackColor = Color(0xFF757575)
                        )
                    )
                }
            }

            // ── PERMISSION ALERT BANNER (IF MISSING) ────────
            if (!hasPhonePermission || !hasNotificationPermission || !hasAudioPermission) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    border = BorderStroke(1.dp, SuspiciousAmber)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = SuspiciousAmber, modifier = Modifier.size(28.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Permissions Required", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Call state & notification permissions needed for real-time protection.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Button(
                            onClick = {
                                val perms = mutableListOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.READ_CALL_LOG,
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.CALL_PHONE,
                                    Manifest.permission.RECORD_AUDIO
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                    perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                                } else {
                                    perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuspiciousAmber),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Grant", color = SurfaceWhite, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // ── HERO BANNER ────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF1565C0), Color(0xFF1E88E5))),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Stay Safe.\nStay Ahead. 🛡",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = SurfaceWhite
                            )
                            Text(
                                "Real-time AI voice clone & phone scam defense.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SurfaceWhite.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { onNavigate(Screen.ScanHub.route) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite)
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run Security Scan", color = RakshakSetuBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = SurfaceWhite.copy(alpha = 0.25f),
                            modifier = Modifier.size(72.dp)
                        )
                    }
                }
            }

            // ── LIVE PROTECTION STATS ───────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(safeCount, "Safe", SafeGreen, Modifier.weight(1f))
                StatCard(suspiciousCount, "Suspicious", SuspiciousAmber, Modifier.weight(1f))
                StatCard(blockedCount, "Blocked", BlockedRed, Modifier.weight(1f))
                StatCard(totalScanned, "Scanned", RakshakSetuBlue, Modifier.weight(1f))
            }

            // ── QUICK ACTIONS ──────────────────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Security Scanners", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("See All", style = MaterialTheme.typography.labelMedium, color = RakshakSetuBlue,
                        modifier = Modifier.clickable { onNavigate(Screen.ScanHub.route) })
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard("Call\nSecurity", Icons.Filled.Phone, Color(0xFFE3F2FD), RakshakSetuBlue) { onNavigate(Screen.CallSecurity.route) }
                    QuickActionCard("Link\nChecker", Icons.Filled.Link, Color(0xFFE8F5E9), SafeGreen) { onNavigate(Screen.LinkChecker.route) }
                    QuickActionCard("QR\nScanner", Icons.Filled.QrCodeScanner, Color(0xFFF3E5F5), AIPurple) { onNavigate(Screen.QRScanner.route) }
                    QuickActionCard("File\nScanner", Icons.Filled.FileCopy, Color(0xFFFFF3E0), SuspiciousAmber) { onNavigate(Screen.FileScanner.route) }
                }
            }

            // ── THREAT SIMULATION & TEST STUDIO ─────────────
            SectionCard {
                Text("🧪 Threat Simulation Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Test on-device AI voice cloning & scam detection scenarios instantly:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { triggerScenario(FakePipelineEmitter.voiceCloneResult(), "AI Voice Clone Attack") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF880E4F)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("🎭 Test AI Voice Clone Attack (SIH26104)", fontWeight = FontWeight.Bold, color = SurfaceWhite, fontSize = 13.sp)
                }

                Spacer(Modifier.height(6.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { triggerScenario(FakePipelineEmitter.digitalArrestResult(), "CBI Digital Arrest") },
                        colors = ButtonDefaults.buttonColors(containerColor = BlockedRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🚨 Digital Arrest", fontSize = 11.sp, color = SurfaceWhite, maxLines = 1)
                    }
                    Button(
                        onClick = { triggerScenario(FakePipelineEmitter.kycFraudResult(), "Bank KYC Fraud") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("💳 Bank KYC Scam", fontSize = 11.sp, color = SurfaceWhite, maxLines = 1)
                    }
                }

                Spacer(Modifier.height(6.dp))

                OutlinedButton(
                    onClick = { triggerScenario(FakePipelineEmitter.benignResult(), "Safe Legitimate Call") },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("🟢 Test Verified Safe Call", color = SafeGreen, fontSize = 12.sp)
                }
            }

            // ── ON-DEVICE AI MODELS STATUS ──────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("On-Device AI Models", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Offline AI", style = MaterialTheme.typography.labelSmall, color = SafeGreen, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Circle, contentDescription = null, tint = if (asrReady) SafeGreen else SuspiciousAmber, modifier = Modifier.size(10.dp))
                    Text("Speech Recognition (Vosk ASR)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(if (asrReady) "Ready" else "Pending", style = MaterialTheme.typography.labelSmall, color = if (asrReady) SafeGreen else SuspiciousAmber)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Circle, contentDescription = null, tint = if (embedReady) SafeGreen else SuspiciousAmber, modifier = Modifier.size(10.dp))
                    Text("Semantic Phrase Matcher (MiniLM)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(if (embedReady) "Ready" else "Pending", style = MaterialTheme.typography.labelSmall, color = if (embedReady) SafeGreen else SuspiciousAmber)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Circle, contentDescription = null, tint = if (deepfakeReady) SafeGreen else SuspiciousAmber, modifier = Modifier.size(10.dp))
                    Text("Voice Clone Detector (AASIST)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(if (deepfakeReady) "Ready" else "Pending", style = MaterialTheme.typography.labelSmall, color = if (deepfakeReady) SafeGreen else SuspiciousAmber)
                }

                if (modelProgressText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(modelProgressText, style = MaterialTheme.typography.labelSmall, color = RakshakSetuBlue)
                }

                if (!asrReady || !embedReady || !deepfakeReady) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            modelBusy = true
                            scope.launch {
                                if (!asrReady) {
                                    modelProgressText = "Downloading speech model..."
                                    ModelDownloadManager.downloadAsrModel(context, "en").collectLatest { st ->
                                        if (st is ModelDownloadManager.DownloadState.Success) asrReady = true
                                    }
                                }
                                if (!embedReady) {
                                    modelProgressText = "Downloading semantic encoder..."
                                    ModelDownloadManager.downloadEmbeddingModel(context).collectLatest { st ->
                                        if (st is ModelDownloadManager.DownloadState.Success) embedReady = true
                                    }
                                }
                                if (!deepfakeReady) {
                                    modelProgressText = "Downloading AASIST voice model..."
                                    ModelDownloadManager.downloadDeepfakeModel(context).collectLatest { st ->
                                        if (st is ModelDownloadManager.DownloadState.Success) deepfakeReady = true
                                    }
                                }
                                modelProgressText = "All AI models ready!"
                                modelBusy = false
                            }
                        },
                        enabled = !modelBusy,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (modelBusy) "Downloading Models..." else "Download All Offline AI Models (~40 MB)", fontSize = 12.sp)
                    }
                }
            }

            // ── TRUSTED GOVERNMENT SERVICES ────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trusted Government Services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Helplines", style = MaterialTheme.typography.labelMedium, color = RakshakSetuBlue)
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrustedServiceChip("NCRP Portal", Icons.Filled.Gavel, "Report ↗", Modifier.weight(1f)) {
                            context.startActivity(Intent(context, GovtReportWebViewActivity::class.java))
                        }
                        TrustedServiceChip("Chakshu", Icons.Filled.RemoveRedEye, "Report ↗", Modifier.weight(1f)) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sancharsaathi.gov.in/citizen/Home/chakshu"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("Unable to open browser") }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrustedServiceChip("Bank Fraud Mail", Icons.Filled.Mail, "Email ↗", Modifier.weight(1f)) {
                            try {
                                val intent = Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("mailto:report.phishing@cybercrime.gov.in")
                                    putExtra(Intent.EXTRA_SUBJECT, "Cybercrime / Phishing Incident Report")
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("No email app found") }
                            }
                        }
                        TrustedServiceChip("Call 1930", Icons.Filled.Call, "Call Now", Modifier.weight(1f)) {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("Unable to dial 1930") }
                            }
                        }
                    }
                }
            }

            // ── RECENT ANALYSIS DOSSIER ────────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Latest Call Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("View Reports", style = MaterialTheme.typography.labelMedium, color = RakshakSetuBlue,
                        modifier = Modifier.clickable { onNavigate(Screen.Reports.route) })
                }
                Spacer(Modifier.height(12.dp))

                val currentResult = lastResult
                if (currentResult != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentResult.isScam) BlockedRedLight else SafeGreenLight
                        ),
                        modifier = Modifier.fillMaxWidth().clickable { onNavigate(Screen.Reports.route) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Call from ${currentResult.phoneNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                RiskBadge(if (currentResult.isScam) RiskStatus.HIGH_RISK else RiskStatus.SAFE)
                            }
                            Text(
                                text = if (currentResult.isScam)
                                    "🚨 ${currentResult.scamType?.replace('_', ' ') ?: "Scam"} detected (${(currentResult.confidence * 100).toInt()}% confidence)"
                                else
                                    "✅ Verified legitimate call. No threats found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (currentResult.isScam) BlockedRed else SafeGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (currentResult.fullTranscript.isNotBlank()) {
                                Text(
                                    "Transcript: \"${currentResult.fullTranscript.take(120)}...\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No recent calls analyzed yet. Incoming calls will appear here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun TrustedServiceChip(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.dp, BorderColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = name, tint = RakshakSetuBlue, modifier = Modifier.size(22.dp))
            Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(action, style = MaterialTheme.typography.labelSmall, color = RakshakSetuBlue)
        }
    }
}
