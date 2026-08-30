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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.pipeline.DeviceCapabilityManager
import com.rakshaksetu.app.pipeline.ModelDownloadManager
import com.rakshaksetu.app.service.BatteryOptimizationHelper
import com.rakshaksetu.app.ui.GovtReportWebViewActivity
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.data.*
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Dynamic Device Tier Detection
    val deviceTier = remember { DeviceCapabilityManager.detectTier(context) }
    val totalRamGB = remember { String.format(Locale.US, "%.1f", DeviceCapabilityManager.getTotalRamGB(context)) }

    var bannerDismissed by remember { mutableStateOf(false) }

    // Check Android Runtime Permissions (Dynamically updated with lifecycle)
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

    // Recheck permissions whenever the app resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPhonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                hasAudioPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                }
                lastResult = DetectionStore.getLastResult(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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

    // Live counts
    val safeCount = if (lastResult != null && !lastResult!!.isScam) 1 else 0
    val suspiciousCount = if (lastResult != null && lastResult!!.isScam && lastResult!!.confidence < 0.7f) 1 else 0
    val blockedCount = if (lastResult != null && lastResult!!.isScam && lastResult!!.confidence >= 0.7f) 1 else 0
    val totalScanned = if (lastResult != null) 1 else 0

    fun triggerScenario(res: DetectionResult, label: String) {
        try {
            DetectionStore.saveLastResult(context, res)
            lastResult = res
            if (res.isScam) {
                ScamAlertManager(context).showScamAlert(res)
            }
            scope.launch {
                snackbarHostState.showSnackbar("Tested: $label")
            }
        } catch (e: Exception) {
            scope.launch {
                snackbarHostState.showSnackbar("Simulation Error: ${e.message}")
            }
        }
    }

    fun downloadAllModels() {
        if (modelBusy) return
        modelBusy = true
        modelProgressText = "Downloading Full-Tier AI Models (~40 MB)..."
        scope.launch {
            ModelDownloadManager.downloadAllModels(context).collectLatest { status ->
                when (status) {
                    is ModelDownloadManager.DownloadState.Downloading -> {
                        modelProgressText = "Downloading ${status.fileName}: ${(status.progress * 100).toInt()}%"
                    }
                    is ModelDownloadManager.DownloadState.Extracting -> {
                        modelProgressText = "Extracting ${status.fileName}..."
                    }
                    is ModelDownloadManager.DownloadState.Success -> {
                        modelProgressText = "All AI models installed & verified!"
                        asrReady = ModelDownloadManager.isAsrModelReady(context)
                        embedReady = ModelDownloadManager.isEmbeddingModelReady(context)
                        deepfakeReady = ModelDownloadManager.isDeepfakeModelReady(context)
                    }
                    is ModelDownloadManager.DownloadState.Error -> {
                        modelProgressText = "Download: ${status.message}"
                        modelBusy = false
                    }
                    else -> {}
                }
            }
            asrReady = ModelDownloadManager.isAsrModelReady(context)
            embedReady = ModelDownloadManager.isEmbeddingModelReady(context)
            deepfakeReady = ModelDownloadManager.isDeepfakeModelReady(context)
            modelBusy = false
        }
    }

    Scaffold(
        topBar = {
            RakshakSetuTopBar(
                title = "Rakshak Setu",
                onNotificationClick = { onNavigate(Screen.AlertCenter.route) },
                onProfileClick = { onNavigate(Screen.Profile.route) }
            )
        },
        bottomBar = {
            BottomNavBar(currentRoute = Screen.Dashboard.route, onNavigate = onNavigate)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── SHIELD ACTIVE CARD (MATCHING YUGANSH DESIGN) ──
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isShieldActive) SafeGreenLight else Color(0xFFFFF3E0)
                ),
                border = BorderStroke(1.dp, if (isShieldActive) SafeGreen.copy(alpha = 0.3f) else SuspiciousAmber.copy(alpha = 0.3f)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isShieldActive) SafeGreen.copy(alpha = 0.15f) else SuspiciousAmber.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isShieldActive) Icons.Filled.VerifiedUser else Icons.Filled.ShieldMoon,
                            contentDescription = null,
                            tint = if (isShieldActive) SafeGreen else SuspiciousAmber,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isShieldActive) "Shield Active — Protected" else "Shield Paused",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isShieldActive) SafeGreen else SuspiciousAmber
                        )
                        Text(
                            text = if (isShieldActive) "On-device AI actively monitoring scam & voice clone attacks" else "Protection paused. Tap switch to re-enable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
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
                            checkedTrackColor = SafeGreen,
                            uncheckedThumbColor = SurfaceWhite,
                            uncheckedTrackColor = BorderColor
                        )
                    )
                }
            }

            // ── PERMISSION ALERT BANNER (IF MISSING & NOT DISMISSED) ────────
            val showBanner = (!hasPhonePermission || !hasAudioPermission || !hasNotificationPermission) && !bannerDismissed
            if (showBanner) {
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
                            Text("Call state & audio permissions needed for real-time protection.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
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
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Grant", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { bannerDismissed = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // ── HERO BANNER CARD ────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(RakshakSetuBlue, Color(0xFF1976D2), RakshakSetuBlueDark)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Stay Safe.\nStay Ahead. 🛡️",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = SurfaceWhite,
                                    lineHeight = 32.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Real-time AI voice clone & phone scam defense.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SurfaceWhite.copy(alpha = 0.85f)
                                )
                            }
                            Icon(
                                Icons.Filled.Shield,
                                contentDescription = null,
                                tint = SurfaceWhite.copy(alpha = 0.2f),
                                modifier = Modifier.size(72.dp)
                            )
                        }

                        Button(
                            onClick = { onNavigate(Screen.CallSecurity.route) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite)
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Run Security Scan", color = RakshakSetuBlue, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── STATS ROW ───────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(count = safeCount, label = "Safe", color = SafeGreen, modifier = Modifier.weight(1f))
                StatCard(count = suspiciousCount, label = "Suspicious", color = SuspiciousAmber, modifier = Modifier.weight(1f))
                StatCard(count = blockedCount, label = "Blocked", color = BlockedRed, modifier = Modifier.weight(1f))
                StatCard(count = totalScanned, label = "Scanned", color = RakshakSetuBlue, modifier = Modifier.weight(1f))
            }

            // ── SECURITY SCANNERS GRID ──────────────────────
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
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard("Call Security", Icons.Filled.Phone, Color(0xFFE3F2FD), RakshakSetuBlue) { onNavigate(Screen.CallSecurity.route) }
                    QuickActionCard("Link Checker", Icons.Filled.Link, Color(0xFFE8F5E9), SafeGreen) { onNavigate(Screen.LinkChecker.route) }
                    QuickActionCard("QR Scanner", Icons.Filled.QrCodeScanner, Color(0xFFF3E5F5), AIPurple) { onNavigate(Screen.QRScanner.route) }
                    QuickActionCard("File Scanner", Icons.Filled.FileCopy, Color(0xFFFFF3E0), SuspiciousAmber) { onNavigate(Screen.FileScanner.route) }
                    QuickActionCard("Image Scanner", Icons.Filled.Image, Color(0xFFFFEBEE), BlockedRed) { onNavigate(Screen.ImageScanner.route) }
                }
            }

            // ── THREAT SIMULATION STUDIO ────────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🧪 Threat Simulation Studio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(color = AIPurpleLight, shape = RoundedCornerShape(6.dp)) {
                        Text("1-Tap Testing", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = AIPurple, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Test on-device AI voice cloning & scam detection scenarios instantly:", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = { triggerScenario(FakePipelineEmitter.voiceCloneResult(), "AI Voice Clone Attack (SIH26104)") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF880E4F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = SurfaceWhite)
                    Spacer(Modifier.width(8.dp))
                    Text("🎭 Test AI Voice Clone Attack (SIH26104)", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { triggerScenario(FakePipelineEmitter.digitalArrestResult(), "Digital Arrest Scam") },
                        colors = ButtonDefaults.buttonColors(containerColor = BlockedRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("🚨 Digital Arrest", color = SurfaceWhite, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }

                    Button(
                        onClick = { triggerScenario(FakePipelineEmitter.kycFraudResult(), "Bank KYC Fraud") },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp)
                    ) {
                        Text("💳 Bank KYC Scam", color = SurfaceWhite, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { triggerScenario(FakePipelineEmitter.benignResult(), "Verified Safe Call") },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, SafeGreen),
                    modifier = Modifier.fillMaxWidth().height(46.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("🟢 Test Verified Safe Call", color = SafeGreen, fontWeight = FontWeight.Bold)
                }
            }

            // ── ON-DEVICE AI MODELS STATUS (FULL TIER DYNAMIC) ──
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (deviceTier == DeviceCapabilityManager.AiTier.FULL) "On-Device AI Engine (Full Tier)" else "On-Device AI Engine (Lite Tier)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "RAM: $totalRamGB GB · ${if (deviceTier == DeviceCapabilityManager.AiTier.FULL) "High Performance Mode" else "Low Memory Mode"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Surface(color = SafeGreenLight, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            if (deviceTier == DeviceCapabilityManager.AiTier.FULL) "FULL TIER" else "LITE TIER",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                val asrLabel = if (deviceTier == DeviceCapabilityManager.AiTier.FULL) "Speech Recognition (IndicConformer)" else "Speech Recognition (Vosk ASR Lite)"
                val embedLabel = if (deviceTier == DeviceCapabilityManager.AiTier.FULL) "Semantic Matcher (MiniLM Multilingual)" else "Semantic Matcher (MiniLM Lite)"
                val cloneLabel = if (deviceTier == DeviceCapabilityManager.AiTier.FULL) "Voice Clone Detector (AASIST Full GNN)" else "Voice Clone Detector (AASIST-L)"

                ModelStatusRow(asrLabel, asrReady)
                ModelStatusRow(embedLabel, embedReady)
                ModelStatusRow(cloneLabel, deepfakeReady)

                if (modelProgressText.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(modelProgressText, style = MaterialTheme.typography.bodySmall, color = RakshakSetuBlue)
                }

                if (!asrReady || !embedReady || !deepfakeReady) {
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton(
                        text = if (modelBusy) "Downloading Models..." else "Download Offline AI Models (~40 MB)",
                        onClick = { downloadAllModels() },
                        enabled = !modelBusy,
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.CloudDownload
                    )
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
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrustedServiceChip("NCRP Portal", Icons.Filled.Gavel, "Report ↗", Modifier.weight(1f)) {
                            context.startActivity(Intent(context, GovtReportWebViewActivity::class.java).apply {
                                if (lastResult != null) {
                                    putExtra(GovtReportWebViewActivity.EXTRA_CALL_ID, lastResult!!.callId)
                                }
                            })
                        }
                        TrustedServiceChip("Chakshu", Icons.Filled.RemoveRedEye, "Report ↗", Modifier.weight(1f)) {
                            context.startActivity(Intent(context, GovtReportWebViewActivity::class.java).apply {
                                putExtra(GovtReportWebViewActivity.EXTRA_PORTAL, "CHAKSHU")
                                if (lastResult != null) {
                                    putExtra(GovtReportWebViewActivity.EXTRA_CALL_ID, lastResult!!.callId)
                                }
                            })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrustedServiceChip("Bank Fraud Mail", Icons.Filled.Mail, "Email ↗", Modifier.weight(1f)) {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:report.phishing@cybercrime.gov.in")
                                putExtra(Intent.EXTRA_SUBJECT, "Urgent: Scam Report from Rakshak Setu")
                            }
                            context.startActivity(intent)
                        }
                        TrustedServiceChip("Call 1930", Icons.Filled.Call, "Call Now", Modifier.weight(1f)) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                            context.startActivity(intent)
                        }
                    }
                }
            }

            // ── LATEST CALL ANALYSIS DOSSIER ───────────────
            if (lastResult != null) {
                val r = lastResult!!
                val epochMs = if (r.callEndEpoch > 100_000_000_000L) r.callEndEpoch else r.callEndEpoch * 1000L
                val timeStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(epochMs))

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
                    Spacer(Modifier.height(10.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = if (r.isScam) BlockedRedLight else SafeGreenLight)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Call from ${r.phoneNumber}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                RiskBadge(if (r.isScam) RiskStatus.HIGH_RISK else RiskStatus.SAFE)
                            }
                            Text(
                                text = if (r.isScam) "🚨 ${r.scamType?.replace('_', ' ')} (${(r.confidence * 100).toInt()}% confidence)" else "✅ Call verified safe — No threat patterns detected",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (r.isScam) BlockedRed else SafeGreen
                            )
                            Text(
                                text = "Recorded: $timeStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            if (r.fullTranscript.isNotBlank()) {
                                Text(
                                    text = "Transcript: \"${r.fullTranscript}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ModelStatusRow(label: String, isReady: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isReady) SafeGreen else SuspiciousAmber)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            color = if (isReady) SafeGreenLight else SuspiciousAmberLight,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = if (isReady) "Ready" else "Pending",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isReady) SafeGreen else SuspiciousAmber,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                softWrap = false
            )
        }
    }
}

@Composable
fun TrustedServiceChip(
    name: String,
    icon: ImageVector,
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
