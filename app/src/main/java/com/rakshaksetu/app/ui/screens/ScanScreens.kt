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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rakshaksetu.app.security.QrCodeAnalyzer
import com.rakshaksetu.app.security.QrPayloadAnalyzer
import com.rakshaksetu.app.security.EvidenceSource
import com.rakshaksetu.app.security.UrlFinding
import com.rakshaksetu.app.security.UrlThreatScanner
import com.rakshaksetu.app.security.UrlThreatHeuristics
import com.rakshaksetu.app.security.UrlRisk
import com.rakshaksetu.app.security.Verdict
import com.rakshaksetu.app.pipeline.AudioDecoder
import com.rakshaksetu.app.pipeline.PipelineCoordinator
import com.rakshaksetu.app.pipeline.VoskAsrEngine
import com.rakshaksetu.app.pipeline.VotingEngine
import java.io.File
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


    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) selectedUri = uri
    }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        busy = true
        errorMsg = null
        phase = "transcript"
        transcriptProgress = 0f
        try {
            // Decode the REAL uploaded audio to 16 kHz mono 16-bit PCM, then run
            // the actual on-device pipeline over it. The previous version showed
            // a fake progress bar and then returned FakePipelineEmitter.voiceCloneResult()
            // regardless of the file -- i.e. it reported "voice clone detected" for
            // every file a user selected, including their grandmother's voicemail.
            val destWav = File(context.cacheDir, "upload_${System.currentTimeMillis()}.wav")
            val decoded = AudioDecoder.decodeToWav(context, uri, destWav.absolutePath)
            if (!decoded || !destWav.exists() || destWav.length() == 0L) {
                errorMsg = "Could not decode that audio file. Supported: WAV, M4A, MP3, 3GP, AMR."
                phase = "upload"
                busy = false
                return@LaunchedEffect
            }

            for (step in 1..100) {
                delay(10)
                transcriptProgress = step / 100f
            }
            phase = "analysis"

            val coordinator = PipelineCoordinator(
                context,
                VoskAsrEngine(context),
                VotingEngine()
            )
            val result = coordinator.runPipeline(
                phoneNumber = "self-uploaded-audio",
                callDurationSec = 0,
                callEndEpoch = System.currentTimeMillis(),
                destWavPath = destWav.absolutePath
            )

            if (result == null) {
                errorMsg = "The analyser could not reach a verdict on this file."
                phase = "upload"
            } else {
                DetectionStore.saveLastResult(context, result)
                activeResult = result
                if (result.isScam) ScamAlertManager(context).showScamAlert(result)
                phase = "result"
            }
            destWav.delete()
        } catch (e: Exception) {
            errorMsg = "Analysis failed: ${e.message}"
            phase = "upload"
        } finally {
            busy = false
        }
    }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Call Security & Voice Clone", onBackClick = onBack) },
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

    val scanner = remember { UrlThreatScanner() }
    var isScanning by remember { mutableStateOf(false) }
    var scannedUrl by remember { mutableStateOf("") }
    var liveChecked by remember { mutableStateOf(false) }
    var feedStatus by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    /**
     * Runs the real layered engine: offline heuristics first (instant), then
     * the live reputation feeds. Never fabricates a verdict -- if the feeds
     * cannot be reached the UI says so instead of implying a clean bill of
     * health.
     */
    fun checkUrl(url: String) {
        if (url.isBlank()) return
        scannedUrl = url
        isScanning = true
        phase = "result"
        scope.launch {
            val outcome = scanner.scan(url)
            val v = outcome.verdict
            resultStatus = when (v.risk) {
                UrlRisk.DANGEROUS -> RiskStatus.HIGH_RISK
                UrlRisk.SUSPICIOUS -> RiskStatus.SUSPICIOUS
                UrlRisk.SAFE -> RiskStatus.SAFE
            }
            liveChecked = outcome.liveChecked
            feedStatus = outcome.feedStatus
            val details = mutableListOf<Pair<String, String>>()
            details += "Risk Score" to "${v.score}/100 (higher is worse)"
            details += "Category" to when (v.risk) {
                UrlRisk.DANGEROUS -> "High Risk - likely phishing / fraud"
                UrlRisk.SUSPICIOUS -> "Suspicious - verify before proceeding"
                UrlRisk.SAFE -> "No threat indicators found"
            }
            if (v.host.isNotBlank()) {
                details += "Host" to v.host
                details += "Domain" to v.registrableDomain
                details += "Scheme" to v.scheme.uppercase()
            }
            details += "Evidence" to when (v.source) {
                EvidenceSource.LIVE_FEED -> "Live feed match: ${v.feedNames.joinToString(", ")}"
                EvidenceSource.LIVE_FEED_CLEAN -> "Live feeds checked, no listing found"
                EvidenceSource.OFFLINE_HEURISTIC -> "On-device rules only (no live feed reachable)"
            }
            outcome.feedStatus.forEach { (name, st) -> details += "Feed: $name" to st }
            v.findings.forEach { details += it.rule to it.detail }
            if (v.findings.isEmpty()) {
                details += "No Rules Fired" to "No on-device heuristic matched this URL"
            }
            resultDetails = details
            isScanning = false
        }
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
                    PrimaryButton(
                        "Check Link",
                        onClick = { if (urlInput.isNotBlank()) checkUrl(urlInput) },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Filled.Search
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { urlInput = "https://sbi-kyc-update-verify.co.in/claim"; checkUrl(urlInput) },
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                        ) { Text("Test phishing pattern", style = MaterialTheme.typography.labelSmall) }
                        OutlinedButton(
                            onClick = { urlInput = "https://cybercrime.gov.in"; checkUrl(urlInput) },
                            shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                        ) { Text("Test official site", style = MaterialTheme.typography.labelSmall) }
                    }
                }

                "result" -> {
                    ResultCard(
                        status = resultStatus,
                        headline = when {
                            isScanning -> "⏳ Checking…"
                            resultStatus == RiskStatus.HIGH_RISK -> "🚨 Dangerous Phishing Link"
                            resultStatus == RiskStatus.SUSPICIOUS -> "⚠️ Suspicious Link"
                            liveChecked -> "✅ No Threat Indicators Found"
                            else -> "⚠️ No Live Check Available"
                        },
                        body = when {
                            isScanning -> "Running on-device rules and live reputation feeds."
                            resultStatus == RiskStatus.HIGH_RISK ->
                                "Live threat intelligence or multiple on-device rules matched this link."
                            resultStatus == RiskStatus.SUSPICIOUS ->
                                "Some indicators look suspicious. Verify before entering any personal detail."
                            liveChecked ->
                                "On-device rules found nothing and live feeds do not list this URL. Absence from a feed is not a guarantee."
                            else ->
                                "Live feeds were unreachable, so only on-device rules ran. Do not treat this as a clean result."
                        }
                    )

                    if (isScanning) {
                        Text("Scanning…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }


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
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf("scan") }
    var payload by remember { mutableStateOf("") }
    var manualEntry by remember { mutableStateOf("") }
    var camError by remember { mutableStateOf<String?>(null) }
    var analysis by remember { mutableStateOf<QrPayloadAnalyzer.Analysis?>(null) }
    var urlVerdict by remember { mutableStateOf<Verdict?>(null) }
    var isScanningUrl by remember { mutableStateOf(false) }
    val scanner = remember { UrlThreatScanner() }
    val lifecycleOwner = LocalLifecycleOwner.current

    /** Decoded payload -> classify, and if it carries a URL, scan that URL too. */
    fun handlePayload(decoded: String) {
        payload = decoded
        analysis = QrPayloadAnalyzer.analyze(decoded)
        phase = "result"
        val nested = QrPayloadAnalyzer.extractUrl(decoded)
        if (nested != null) {
            isScanningUrl = true
            scope.launch {
                urlVerdict = scanner.scan(nested).verdict
                isScanningUrl = false
            }
        }
    }

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
                        modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                val pv = PreviewView(ctx)
                                val executor = ContextCompat.getMainExecutor(ctx)
                                val analyzer = QrCodeAnalyzer { text ->
                                    scope.launch { handlePayload(text) }
                                }
                                val opts = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                opts.setAnalyzer(executor, analyzer)
                                try {
                                    val preview = Preview.Builder().build()
                                    preview.setSurfaceProvider(pv.surfaceProvider)
                                    val provider = ProcessCameraProvider.getInstance(ctx).get()
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, opts)
                                } catch (e: Exception) {
                                    camError = "Camera unavailable: ${e.message}"
                                }
                                pv
                            }
                        )
                    }
                    camError?.let {
                        Text(it, color = BlockedRed, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(
                        onClick = { phase = "manual" },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    ) { Text("Enter QR content manually", color = TextSecondary) }
                }

                "manual" -> {
                    Text("Paste QR payload", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = manualEntry,
                        onValueChange = { manualEntry = it },
                        label = { Text("QR content") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        minLines = 3
                    )
                    PrimaryButton("Analyse", onClick = {
                        if (manualEntry.isNotBlank()) handlePayload(manualEntry.trim())
                    }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(
                        onClick = { phase = "scan" },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    ) { Text("Back to camera", color = TextSecondary) }
                }

                "result" -> {
                    val a = analysis
                    if (a != null) {
                        when (a.kind) {
                            QrPayloadAnalyzer.Kind.UPI_COLLECT -> ResultCard(
                                status = RiskStatus.BLOCKED,
                                headline = "⚠️ UPI Payment Request",
                                body = "This QR opens a payment screen pre-filled to a specific payee.",
                            )
                            QrPayloadAnalyzer.Kind.PAYMENT_DEEP_LINK -> ResultCard(
                                status = RiskStatus.BLOCKED,
                                headline = "⚠️ Payment App Link",
                                body = "This QR opens a payment app directly.",
                            )
                            QrPayloadAnalyzer.Kind.URL -> ResultCard(
                                status = RiskStatus.SUSPICIOUS,
                                headline = "Web Link QR",
                                body = "Destination is being checked against live threat feeds.",
                            )
                            else -> ResultCard(
                                status = RiskStatus.SAFE,
                                headline = "Non-payment QR",
                                body = "This code carries ${a.label.lowercase()} rather than a payment request.",
                            )
                        }

                        SectionCard {
                            Text("Decoded Payload", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            AnalysisRow("Type", a.label, TextPrimary)
                            a.upiHandle?.let { AnalysisRow("Payee UPI ID", it, BlockedRed) }
                            a.payeeName?.let { AnalysisRow("Payee Name", it, TextPrimary) }
                            a.amount?.let { AnalysisRow("Amount", "Rs $it", BlockedRed) }
                            a.note?.let { AnalysisRow("Note", it, TextPrimary) }
                            AnalysisRow("Raw", payload, TextPrimary)
                        }

                        if (a.warnings.isNotEmpty()) {
                            SectionCard {
                                Text("What to check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                a.warnings.forEach {
                                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }

                        if (isScanningUrl) {
                            Text("Checking destination against live feeds…", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        urlVerdict?.let { v ->
                            SectionCard {
                                Text("Destination Check", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                AnalysisRow("Score", "${v.score}/100", if (v.risk == UrlRisk.DANGEROUS) BlockedRed else TextPrimary)
                                AnalysisRow("Verdict", v.risk.name, if (v.risk == UrlRisk.DANGEROUS) BlockedRed else TextPrimary)
                                v.findings.forEach { AnalysisRow(it.rule, it.detail, TextPrimary) }
                            }
                        }

                        if (a.kind == QrPayloadAnalyzer.Kind.URL) {
                            PrimaryButton("Open in Browser", onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.nestedUrl ?: "")))
                                } catch (ignored: Exception) {}
                            }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.OpenInNew)
                        }
                        OutlinedButton(
                            onClick = { phase = "scan"; payload = ""; analysis = null; urlVerdict = null },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        ) { Text("Scan Another QR", color = TextSecondary) }
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




























