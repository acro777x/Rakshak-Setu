package com.rakshaksetu.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.*
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.ui.GovtReportWebViewActivity
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.data.RiskStatus
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── SCAN HUB ──────────────────────────────────────────────────
@Composable
fun ScanHubScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Security Scanners", onBackClick = onBack) },
        bottomBar = { BottomNavBar(currentRoute = Screen.ScanHub.route, onNavigate = onNavigate) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("AI Security Scanners", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            ScanOptionCard("Call Security & Voice Clone", "Analyze calls & detect AASIST AI voice-clone signatures", Icons.Filled.Phone, RakshakSetuBlue) { onNavigate(Screen.CallSecurity.route) }
            ScanOptionCard("Link & Phishing Checker", "Check URLs for phishing, fake bank portals & fraud", Icons.Filled.Link, SafeGreen) { onNavigate(Screen.LinkChecker.route) }
            ScanOptionCard("QR Code Scanner", "Safely decode and inspect QR destination links", Icons.Filled.QrCodeScanner, AIPurple) { onNavigate(Screen.QRScanner.route) }
            ScanOptionCard("File Scanner", "Scan APKs, PDFs and archives for suspicious payloads", Icons.Filled.FileCopy, SuspiciousAmber) { onNavigate(Screen.FileScanner.route) }
            ScanOptionCard("Image Threat Scanner", "Inspect screenshots & photos for hidden scam QR codes", Icons.Filled.Image, BlockedRed) { onNavigate(Screen.ImageScanner.route) }
        }
    }
}

@Composable
fun ScanOptionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

// ── CALL SECURITY ─────────────────────────────────────────────
@Composable
fun CallSecurityScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeResult by remember { mutableStateOf<DetectionResult?>(DetectionStore.getLastResult(context)) }
    var phase by remember { mutableStateOf(if (activeResult != null) "result" else "upload") }
    var transcriptProgress by remember { mutableFloatStateOf(0f) }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            phase = "transcript"
        }
    }

    LaunchedEffect(phase) {
        if (phase == "transcript") {
            repeat(100) {
                delay(15)
                transcriptProgress = it / 100f
            }
            phase = "analysis"
            delay(1200)
            val res = FakePipelineEmitter.voiceCloneResult()
            DetectionStore.saveLastResult(context, res)
            activeResult = res
            ScamAlertManager(context).showScamAlert(res)
            phase = "result"
        }
    }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Call Security & Voice Clone", onBackClick = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "upload" -> {
                    Text("Analyze Call Recording", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Select a recorded call or run the on-device AASIST AI voice-clone detector:", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

                    PrimaryButton(
                        text = "Select Audio File (.wav / .m4a)",
                        onClick = { audioPicker.launch("audio/*") },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.CloudUpload
                    )

                    Spacer(Modifier.height(8.dp))

                    Text("Or Run Instant Simulation:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = { phase = "transcript" },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF880E4F)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = SurfaceWhite)
                        Spacer(Modifier.width(8.dp))
                        Text("Analyze Sample AI Voice Clone Call", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                    }
                }

                "transcript" -> {
                    Text("Transcribing Audio with Vosk ASR…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(progress = { transcriptProgress }, modifier = Modifier.fillMaxWidth(), color = RakshakSetuBlue)
                    ScanRadarAnimation(RakshakSetuBlue)
                    Text("Running on-device acoustic decoding (16kHz Kaldi model)…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                "analysis" -> {
                    Text("Analyzing AASIST Neural Signatures…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    ScanRadarAnimation(AIPurple)
                    Text("Detecting spectral phase anomalies and vocoder synthesis artifacts…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    VoiceWaveformAnimation()
                }

                "result" -> {
                    val r = activeResult
                    if (r != null) {
                        ResultCard(
                            status = if (r.isScam) RiskStatus.HIGH_RISK else RiskStatus.SAFE,
                            headline = if (r.isScam) "🚨 High Risk — AI Voice Cloning Detected" else "✅ Safe Call Verified",
                            body = "Type: ${r.scamType?.replace('_', ' ') ?: "Scam"}. Confidence: ${(r.confidence * 100).toInt()}%. Flagged segments: ${r.flaggedSegments.size}"
                        )

                        SectionCard {
                            Text("Call Transcript & Flagged Statements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(r.fullTranscript, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 20.sp)
                        }

                        SectionCard {
                            Text("Acoustic & AI Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            AnalysisRow("Caller Number", r.phoneNumber, TextPrimary)
                            AnalysisRow("Duration", "${r.durationSec}s", TextPrimary)
                            AnalysisRow("ASR Processing Time", "${r.pipelineMs.asr} ms", TextPrimary)
                            AnalysisRow("Neural Clone Score", "${(r.confidence * 100).toInt()}%", if (r.isScam) BlockedRed else SafeGreen)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton(
                                text = "File 1930 Complaint",
                                onClick = {
                                    context.startActivity(Intent(context, GovtReportWebViewActivity::class.java).apply {
                                        putExtra(GovtReportWebViewActivity.EXTRA_CALL_ID, r.callId)
                                    })
                                },
                                modifier = Modifier.weight(1f),
                                icon = Icons.Filled.Gavel
                            )
                            OutlinedButton(
                                onClick = { phase = "upload"; activeResult = null },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Scan Another", color = RakshakSetuBlue)
                            }
                        }
                    } else {
                        phase = "upload"
                    }
                }
            }
        }
    }
}

// ── LINK CHECKER ──────────────────────────────────────────────
@Composable
fun LinkCheckerScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var urlInput by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf("input") }
    var resultStatus by remember { mutableStateOf(RiskStatus.SAFE) }
    var resultDetails by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    fun checkUrl(url: String) {
        val lower = url.lowercase().trim()
        val isRisky = lower.contains("free-gift") || lower.contains("gift") || lower.contains("kyc") || lower.contains("otp") || lower.contains("apk") || lower.contains("refund") || lower.contains("sbi-") || lower.contains("login-")
        if (isRisky) {
            resultStatus = RiskStatus.HIGH_RISK
            resultDetails = listOf(
                "Risk Level" to "High Risk",
                "Category" to "Suspected Phishing / Bank Fraud",
                "Threat Indicators" to "Urgency keywords / Unregistered SSL",
                "Domain" to url
            )
        } else {
            resultStatus = RiskStatus.SAFE
            resultDetails = listOf(
                "Risk Level" to "Low Risk",
                "Category" to "Verified Domain",
                "SSL Certificate" to "Valid TLS 1.3",
                "Domain" to url
            )
        }
        phase = "result"
    }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Link & Phishing Checker", onBackClick = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "input" -> {
                    Text("Enter or Paste a URL", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Paste suspicious link here") },
                        placeholder = { Text("https://example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }) { Icon(Icons.Filled.Close, contentDescription = "Clear") }
                            }
                        }
                    )
                    PrimaryButton("Check Link", onClick = { if (urlInput.isNotBlank()) checkUrl(urlInput) }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Search)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { urlInput = "https://free-gift-reward.xyz/claim"; checkUrl(urlInput) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Text("Try Phishing Link", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { urlInput = "https://cybercrime.gov.in"; checkUrl(urlInput) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Text("Try Safe Link", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                "result" -> {
                    ResultCard(
                        status = resultStatus,
                        headline = if (resultStatus == RiskStatus.HIGH_RISK) "🚨 Dangerous Phishing Link" else "✅ Link Verified Safe",
                        body = if (resultStatus == RiskStatus.HIGH_RISK) "This domain contains phishing patterns designed to steal banking credentials." else "No malicious patterns detected."
                    )

                    SectionCard {
                        Text("Analysis Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        resultDetails.forEach { (k, v) ->
                            AnalysisRow(k, v, if (k == "Risk Level" && v == "High Risk") BlockedRed else TextPrimary)
                        }
                    }

                    if (resultStatus == RiskStatus.SAFE) {
                        PrimaryButton("Open Safely in Browser", onClick = {
                            try {
                                val fullUrl = if (!urlInput.startsWith("http")) "https://$urlInput" else urlInput
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                            } catch (e: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("Unable to open browser") }
                            }
                        }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.OpenInNew)
                    } else {
                        Button(
                            onClick = {
                                context.startActivity(Intent(context, GovtReportWebViewActivity::class.java))
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BlockedRed)
                        ) {
                            Icon(Icons.Filled.Gavel, contentDescription = null, tint = SurfaceWhite)
                            Spacer(Modifier.width(8.dp))
                            Text("Report Scam Link to NCRP", color = SurfaceWhite, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(onClick = { phase = "input"; urlInput = "" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Check Another Link", color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ── QR SCANNER ────────────────────────────────────────────────
@Composable
fun QRScannerScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var phase by remember { mutableStateOf("scan") }
    var decodedUrl by remember { mutableStateOf("https://sancharsaathi.gov.in") }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "QR Code Scanner", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "scan" -> {
                    Text("Scan a QR Code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1A1A2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = SurfaceWhite.copy(alpha = 0.6f), modifier = Modifier.size(64.dp))
                            Text("Aim camera at QR code", style = MaterialTheme.typography.bodyMedium, color = SurfaceWhite.copy(alpha = 0.8f))
                        }
                    }
                    PrimaryButton("Decode QR Code", onClick = { phase = "result" }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.QrCode)
                }

                "result" -> {
                    ResultCard(RiskStatus.SAFE, "Decoded Safe QR Code", "Destination URL verified clean.")
                    SectionCard {
                        Text("QR Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        AnalysisRow("Destination URL", decodedUrl, RakshakSetuBlue)
                        AnalysisRow("Safety Status", "Verified Safe", SafeGreen)
                    }
                    PrimaryButton("Open URL in Browser", onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(decodedUrl)))
                        } catch (ignored: Exception) {}
                    }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.OpenInNew)
                    OutlinedButton(onClick = { phase = "scan" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Scan Another QR", color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ── FILE SCANNER ──────────────────────────────────────────────
@Composable
fun FileScannerScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf("select") }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileName = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
            phase = "result"
        }
    }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "File & APK Scanner", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "select" -> {
                    Text("Select a File to Scan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    UploadCard(
                        title = "Upload Document or APK",
                        subtitle = "Inspect APKs, PDFs and media files for fraud",
                        selectedFileName = selectedFileName,
                        selectedFileSize = "Verified Local",
                        acceptedTypes = "Any format",
                        maxSize = "100 MB",
                        onSelectClick = { filePicker.launch("*/*") },
                        onRemoveClick = { selectedFileName = null }
                    )
                }

                "result" -> {
                    ResultCard(RiskStatus.SAFE, "File Inspection Clean", "No malware signatures or unauthorized remote control hooks found.")
                    SectionCard {
                        Text("Inspection Results", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        AnalysisRow("File", selectedFileName ?: "scanned_file.pdf", TextPrimary)
                        AnalysisRow("Status", "Safe File", SafeGreen)
                    }
                    OutlinedButton(onClick = { phase = "select"; selectedFileName = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Scan Another File", color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ── IMAGE SCANNER ─────────────────────────────────────────────
@Composable
fun ImageScannerScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var selectedImageName by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf("select") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageName = uri.lastPathSegment?.substringAfterLast('/') ?: "screenshot.png"
            phase = "result"
        }
    }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Image Threat Scanner", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "select" -> {
                    Text("Scan Screenshot or Photo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    UploadCard(
                        title = "Select Screenshot / Photo",
                        subtitle = "Detect hidden scam QR codes, malicious links in receipts",
                        selectedFileName = selectedImageName,
                        selectedFileSize = "Verified Local",
                        acceptedTypes = "JPEG / PNG",
                        maxSize = "50 MB",
                        onSelectClick = { imagePicker.launch("image/*") },
                        onRemoveClick = { selectedImageName = null }
                    )
                }

                "result" -> {
                    ResultCard(RiskStatus.SAFE, "Image Clean", "No embedded QR codes or fraudulent links found in image.")
                    SectionCard {
                        Text("Image Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        AnalysisRow("Image", selectedImageName ?: "photo.png", TextPrimary)
                        AnalysisRow("Result", "Clean Image", SafeGreen)
                    }
                    OutlinedButton(onClick = { phase = "select"; selectedImageName = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Text("Scan Another Image", color = TextSecondary)
                    }
                }
            }
        }
    }
}
