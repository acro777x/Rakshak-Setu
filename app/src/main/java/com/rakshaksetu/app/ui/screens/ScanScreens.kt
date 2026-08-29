package com.rakshaksetu.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rakshaksetu.app.ui.data.RiskStatus
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.delay

// ── SCAN HUB ──────────────────────────────────────────────────
@Composable
fun ScanHubScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { SafeShieldTopBar(title = "Scan", onBackClick = onBack) },
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
            Text("What do you want to scan?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            ScanOptionCard("Call Security", "Analyze calls & detect voice-clone patterns", Icons.Filled.Phone, SafeShieldBlue) { onNavigate(Screen.CallSecurity.route) }
            ScanOptionCard("Link Checker", "Check links for phishing & scams", Icons.Filled.Link, SafeGreen) { onNavigate(Screen.LinkChecker.route) }
            ScanOptionCard("QR Scanner", "Scan QR codes safely", Icons.Filled.QrCodeScanner, AIPurple) { onNavigate(Screen.QRScanner.route) }
            ScanOptionCard("File Scanner", "Scan files for viruses & malware", Icons.Filled.FileCopy, SuspiciousAmber) { onNavigate(Screen.FileScanner.route) }
            ScanOptionCard("Image Scanner", "Scan photos for hidden threats", Icons.Filled.Image, BlockedRed) { onNavigate(Screen.ImageScanner.route) }
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
    var phase by remember { mutableStateOf("upload") } // upload → transcript → analysis → result
    var transcriptProgress by remember { mutableFloatStateOf(0f) }
    val mockTranscript = """
[00:02] Caller: Hello, is this Mr. Sharma?
[00:05] You: Yes, who is calling?
[00:07] Caller: I am calling from your bank support unit...
[00:15] Caller: Your account shows suspicious activity.
[00:22] Caller: Please share the OTP you just received...
    """.trimIndent()

    val recentCalls = listOf(
        "+91 98765 43210" to "High Risk · 21 May 2025",
        "+91 98123 55233" to "July · 21 May 2025",
        "+91 90909 09090" to "Safe · 21 May 2025"
    )

    LaunchedEffect(phase) {
        if (phase == "transcript") {
            repeat(100) {
                delay(30)
                transcriptProgress = it / 100f
            }
            delay(500)
            phase = "analysis"
            delay(2000)
            phase = "result"
        }
    }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Call Security", onBackClick = onBack) },
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
                    Text("Analyze Call", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Tap below to analyze a call recording.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    PrimaryButton(
                        text = "Upload Call Recording",
                        onClick = { phase = "transcript" },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.CloudUpload
                    )

                    Text("Recent Analyses", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    recentCalls.forEach { (number, meta) ->
                        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite), elevation = CardDefaults.cardElevation(1.dp)) {
                            Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Phone, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(22.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(number, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(meta, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }
                                RiskBadge(if (number.contains("43210")) RiskStatus.HIGH_RISK else RiskStatus.SAFE)
                            }
                        }
                    }
                }

                "transcript" -> {
                    Text("Generating Transcript…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { transcriptProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = SafeShieldBlue
                    )
                    ScanRadarAnimation(SafeShieldBlue)
                    Text("Reading call audio locally…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }

                "analysis" -> {
                    Text("Analyzing Voice Patterns…", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    ScanRadarAnimation(AIPurple)
                    Text("Detecting voice-clone indicators. This may take a moment.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    VoiceWaveformAnimation()
                }

                "result" -> {
                    ResultCard(
                        status = RiskStatus.HIGH_RISK,
                        headline = "High Risk — Voice Cloning Suspected",
                        body = "Synthetic voice patterns detected with 82% confidence. Caller may be impersonating a bank agent."
                    )

                    SectionCard {
                        Text("Call Transcript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        Text(mockTranscript, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 20.sp)
                    }

                    SectionCard {
                        Text("Voice Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        AnalysisRow("Voice clone confidence", "82%", BlockedRed)
                        AnalysisRow("Scam script detected", "Yes", BlockedRed)
                        AnalysisRow("Caller ID", "+91 98765 43210", TextPrimary)
                        AnalysisRow("Duration", "3m 42s", TextPrimary)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("Block Number", onClick = {}, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Block)
                        SecondaryButton("Report Incident", onClick = { onNavigate(Screen.ReportStep1.route) }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Flag)
                        OutlinedButton(onClick = { phase = "upload" }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                            Text("Scan Another", color = TextSecondary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceWaveformAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(20) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 8f, targetValue = (20 + (index * 7) % 40).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(300 + index * 60, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "bar$index"
            )
            Box(modifier = Modifier.width(8.dp).height(height.dp).clip(RoundedCornerShape(4.dp)).background(AIPurple.copy(alpha = 0.6f + index * 0.02f)))
        }
    }
}

@Composable
fun AnalysisRow(label: String, value: String, valueColor: Color = TextPrimary) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ── LINK CHECKER ──────────────────────────────────────────────
@Composable
fun LinkCheckerScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var urlInput by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf("input") }
    var result by remember { mutableStateOf<Pair<RiskStatus, Map<String, String>>?>(null) }

    LaunchedEffect(phase) {
        if (phase == "scanning") {
            delay(1800)
            result = if (urlInput.contains("free-gift") || urlInput.contains("phish")) {
                RiskStatus.BLOCKED to mapOf(
                    "URL" to urlInput.ifEmpty { "www.free-gift.store" },
                    "Risk Level" to "High",
                    "Category" to "Phishing",
                    "Domain Age" to "2 days",
                    "SSL Certificate" to "Invalid"
                )
            } else {
                RiskStatus.SAFE to mapOf(
                    "URL" to urlInput.ifEmpty { "https://secure.example.com" },
                    "Risk Level" to "Low",
                    "Category" to "Safe",
                    "Domain Age" to "8 years",
                    "SSL Certificate" to "Valid"
                )
            }
            phase = "result"
        }
    }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Link Checker", onBackClick = onBack) },
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
                "input" -> {
                    Text("Enter or paste a link", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Paste URL here") },
                        placeholder = { Text("https://example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        trailingIcon = {
                            if (urlInput.isNotEmpty()) {
                                IconButton(onClick = { urlInput = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                    PrimaryButton("Check Link", onClick = { phase = "scanning" }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Search)
                    // Demo quick-fill buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { urlInput = "www.free-gift.store" }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Text("Try Risky Link", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(onClick = { urlInput = "https://secure.nmrc.in" }, shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)) {
                            Text("Try Safe Link", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Text("Scan History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScanHistoryRow("https://secure.nmrc.in", "Safe · 21 May 2025", RiskStatus.SAFE)
                            Divider(color = BorderColor)
                            ScanHistoryRow("www.free-gift.store", "Blocked · 21 May 2025", RiskStatus.BLOCKED)
                        }
                    }
                }

                "scanning" -> {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ScanRadarAnimation(SafeShieldBlue)
                            Text("Analyzing link…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                            Text("Checking domain reputation, SSL & phishing indicators", style = MaterialTheme.typography.bodySmall, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }

                "result" -> {
                    result?.let { (status, details) ->
                        ResultCard(
                            status = status,
                            headline = when (status) {
                                RiskStatus.BLOCKED, RiskStatus.HIGH_RISK -> "Dangerous Link Detected"
                                RiskStatus.SUSPICIOUS -> "Suspicious Link"
                                RiskStatus.SAFE -> "Link appears safe"
                            },
                            body = when (status) {
                                RiskStatus.BLOCKED, RiskStatus.HIGH_RISK -> "This link may be phishing or malicious. Do not open."
                                RiskStatus.SUSPICIOUS -> "Proceed with caution."
                                RiskStatus.SAFE -> "No known threats detected."
                            }
                        )

                        SectionCard {
                            Text("Analysis Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            details.forEach { (k, v) ->
                                AnalysisRow(k, v, if (k == "Risk Level" && v == "High") BlockedRed else TextPrimary)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (status == RiskStatus.SAFE) {
                                PrimaryButton("Open Safely", onClick = {}, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.OpenInNew)
                            } else {
                                Button(
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = BlockedRed)
                                ) {
                                    Icon(Icons.Filled.Block, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Avoid This Link", color = SurfaceWhite, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            SecondaryButton("Report Incident", onClick = { onNavigate(Screen.ReportStep1.route) }, modifier = Modifier.fillMaxWidth())
                            OutlinedButton(onClick = { phase = "input"; result = null }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                                Text("Check Another Link", color = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScanHistoryRow(url: String, meta: String, status: RiskStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Link, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(url, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(meta, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        RiskBadge(status)
    }
}

// ── QR SCANNER ────────────────────────────────────────────────
@Composable
fun QRScannerScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var phase by remember { mutableStateOf("scan") }
    var resultStatus by remember { mutableStateOf(RiskStatus.SAFE) }
    var decodedUrl by remember { mutableStateOf("https://secure.example.com") }

    LaunchedEffect(phase) {
        if (phase == "scanning") {
            delay(2000)
            resultStatus = RiskStatus.SAFE
            decodedUrl = "https://secure.nmrc.in"
            phase = "result"
        }
    }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "QR Scanner", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "scan" -> {
                    Text("Scan a QR Code", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    // Mock camera viewfinder
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF1A1A2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = SurfaceWhite.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                            Text("Point camera at QR code", style = MaterialTheme.typography.bodyMedium, color = SurfaceWhite.copy(alpha = 0.7f))
                            Text("(Demo mode — tap below to simulate scan)", style = MaterialTheme.typography.labelSmall, color = SurfaceWhite.copy(alpha = 0.4f))
                        }
                        // Corner markers
                        Box(Modifier.fillMaxSize()) {
                            Box(Modifier.size(40.dp).border(3.dp, SafeShieldBlue, RoundedCornerShape(topStart = 12.dp)).align(Alignment.TopStart).padding(4.dp))
                            Box(Modifier.size(40.dp).border(3.dp, SafeShieldBlue, RoundedCornerShape(topEnd = 12.dp)).align(Alignment.TopEnd).padding(4.dp))
                            Box(Modifier.size(40.dp).border(3.dp, SafeShieldBlue, RoundedCornerShape(bottomStart = 12.dp)).align(Alignment.BottomStart).padding(4.dp))
                            Box(Modifier.size(40.dp).border(3.dp, SafeShieldBlue, RoundedCornerShape(bottomEnd = 12.dp)).align(Alignment.BottomEnd).padding(4.dp))
                        }
                    }
                    PrimaryButton("Simulate QR Scan", onClick = { phase = "scanning" }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.QrCode)
                }

                "scanning" -> {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            ScanRadarAnimation(AIPurple)
                            Text("Decoding QR…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        }
                    }
                }

                "result" -> {
                    ResultCard(RiskStatus.SAFE, "Safe QR Code", "The destination appears to be a trusted website. No threats detected.")
                    SectionCard {
                        Text("QR Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        AnalysisRow("Destination URL", decodedUrl, SafeShieldBlue)
                        AnalysisRow("Safety Verdict", "Safe Link", SafeGreen)
                        AnalysisRow("Domain Age", "5 years", TextPrimary)
                        AnalysisRow("SSL Certificate", "Valid", SafeGreen)
                    }
                    PrimaryButton("Open Link", onClick = {}, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.OpenInNew)
                    SecondaryButton("Save Result", onClick = {}, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { phase = "scan" }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Scan Another", color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ── FILE SCANNER ──────────────────────────────────────────────
@Composable
fun FileScannerScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var phase by remember { mutableStateOf("select") }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var scanProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(phase) {
        if (phase == "scanning") {
            repeat(100) {
                delay(20)
                scanProgress = it / 100f
            }
            delay(300)
            phase = "result"
        }
    }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "File Scanner", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "select" -> {
                    Text("Upload a File to Scan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    UploadCard(
                        title = "Select File",
                        subtitle = "Documents, PDFs, APKs, archives",
                        selectedFileName = selectedFile,
                        selectedFileSize = "248 KB",
                        acceptedTypes = "Any format",
                        maxSize = "50 MB",
                        onSelectClick = { selectedFile = "invoice.pdf" },
                        onRemoveClick = { selectedFile = null }
                    )
                    if (selectedFile != null) {
                        PrimaryButton("Scan File", onClick = { phase = "scanning" }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Search)
                    }

                    Text("Scan History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.FileCopy, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(22.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("report.docx", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Safe · 31 May 2025", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            RiskBadge(RiskStatus.SAFE)
                        }
                    }
                }

                "scanning" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(20.dp))
                        ScanRadarAnimation(SuspiciousAmber)
                        Text("Scanning ${selectedFile ?: "file"}…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        LinearProgressIndicator(progress = { scanProgress }, modifier = Modifier.fillMaxWidth(), color = SuspiciousAmber)
                        Text("${(scanProgress * 100).toInt()}% complete", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }

                "result" -> {
                    ResultCard(RiskStatus.SAFE, "No Known Threats Detected", "File appears clean. No known malware signatures found. Always exercise caution when opening files from unknown sources.")
                    SectionCard {
                        Text("File Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        AnalysisRow("File Name", selectedFile ?: "invoice.pdf", TextPrimary)
                        AnalysisRow("File Type", "PDF", TextPrimary)
                        AnalysisRow("File Size", "248 KB", TextPrimary)
                        AnalysisRow("Scanned On", "21 Aug 2025, 10:30 AM", TextPrimary)
                    }
                    PrimaryButton("View File", onClick = {}, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Visibility)
                    SecondaryButton("Report Incident", onClick = { onNavigate(Screen.ReportStep1.route) }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { phase = "select"; selectedFile = null; scanProgress = 0f }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
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
    var phase by remember { mutableStateOf("select") }
    var selectedImage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(phase) {
        if (phase == "scanning") {
            delay(2200)
            phase = "result"
        }
    }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Image Scanner", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (phase) {
                "select" -> {
                    Text("Scan an Image", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Detect hidden QR codes, suspicious URLs, and embedded metadata.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    UploadCard(
                        title = "Select Image",
                        subtitle = "Screenshots, photos, or downloaded images",
                        selectedFileName = selectedImage,
                        selectedFileSize = "1.2 MB",
                        acceptedTypes = "JPEG / PNG",
                        maxSize = "20 MB",
                        onSelectClick = { selectedImage = "Screenshot_2025.png" },
                        onRemoveClick = { selectedImage = null }
                    )
                    if (selectedImage != null) {
                        PrimaryButton("Scan Image", onClick = { phase = "scanning" }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Image)
                    }
                }

                "scanning" -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 32.dp)) {
                        ScanRadarAnimation(AIPurple)
                        Text("Analyzing image…", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                        Text("Checking for hidden QR codes, URLs, and metadata", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }

                "result" -> {
                    ResultCard(RiskStatus.SUSPICIOUS, "Suspicious Content Detected", "A hidden QR code was found in this image. The encoded URL matches a known suspicious domain.")
                    SectionCard {
                        Text("Findings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(8.dp))
                        AnalysisRow("Hidden QR Code", "Detected", BlockedRed)
                        AnalysisRow("Encoded URL", "www.free-gift.store", BlockedRed)
                        AnalysisRow("URL Risk", "Phishing suspected", SuspiciousAmber)
                        AnalysisRow("Image File", selectedImage ?: "Screenshot.png", TextPrimary)
                    }
                    SecondaryButton("Report Incident", onClick = { onNavigate(Screen.ReportStep1.route) }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { phase = "select"; selectedImage = null }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("Scan Another Image", color = TextSecondary)
                    }
                }
            }
        }
    }
}
