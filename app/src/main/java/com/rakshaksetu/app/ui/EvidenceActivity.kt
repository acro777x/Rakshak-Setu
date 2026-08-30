package com.rakshaksetu.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.app.action.BankEmailAction
import com.rakshaksetu.app.action.GovtPortalAction
import com.rakshaksetu.app.action.HelplineAction
import com.rakshaksetu.app.elder.ElderModeStore
import com.rakshaksetu.app.elder.EmergencyDispatcher
import com.rakshaksetu.app.evidence.StatementGenerator
import com.rakshaksetu.app.feedback.FeedbackLogger
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.receiver.ElderAlertReceiver
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.launch

class EvidenceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra(ScamAlertManager.EXTRA_CALL_ID)
            ?: intent.getStringExtra("CALL_ID")
            ?: "unknown"

        setContent {
            RakshakSetuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = BackgroundLight
                ) {
                    EvidenceScreen(
                        callId = callId,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvidenceScreen(callId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val detectionResult: DetectionResult? = remember(callId) {
        val last = DetectionStore.getLastResult(context)
        when {
            callId == "unknown" -> last
            last?.callId == callId -> last
            else -> DetectionStore.getCachedResult(callId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Scam Alert & Evidence",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SurfaceWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = SurfaceWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFB71C1C)
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundLight
    ) { padding ->
        if (detectionResult == null) {
            EmptyEvidenceState(modifier = Modifier.padding(padding), onBack = onBack)
            return@Scaffold
        }

        EvidenceContent(
            modifier = Modifier.padding(padding),
            result = detectionResult,
            clipboardSetText = { clipboardManager.setText(AnnotatedString(it)) },
            onSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
            onBack = onBack,
            contextTag = context
        )
    }
}

@Composable
private fun EmptyEvidenceState(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.FolderOff,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No evidence available for this call",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Either the call recording could not be located, or the analysis has not run yet.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onBack,
            colors = ButtonDefaults.buttonColors(containerColor = RakshakSetuBlue)
        ) {
            Text("Go Back")
        }
    }
}

@Composable
private fun EvidenceContent(
    modifier: Modifier = Modifier,
    result: DetectionResult,
    clipboardSetText: (String) -> Unit,
    onSnackbar: (String) -> Unit,
    onBack: () -> Unit,
    contextTag: android.content.Context
) {
    val context = contextTag
    val elderStore = remember { ElderModeStore(context) }
    val elderEnabled = elderStore.isEnabled
    val canAlertFamily = elderEnabled &&
        elderStore.getGuardians().isNotEmpty() &&
        EmergencyDispatcher.qualifiesForOneTap(result)

    val statementText = remember(result) {
        try {
            StatementGenerator.getEvidenceStatement(context, result.callId)
                ?: StatementGenerator.generate(result)
        } catch (e: Exception) {
            "Evidence generated for Call ${result.callId.take(8)} from ${result.phoneNumber}. Confidence: ${(result.confidence * 100).toInt()}%."
        }
    }

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── 1. YUGANSH COMPLETE RED ALERT HERO SECTION ──────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF880E4F), Color(0xFFC62828), Color(0xFFD32F2F))
                    )
                )
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(SurfaceWhite.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Dangerous,
                        contentDescription = null,
                        tint = SurfaceWhite,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Surface(
                    color = SurfaceWhite.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        "🚨 CONFIRMED HIGH RISK (${(result.confidence * 100).toInt()}%)",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = SurfaceWhite,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = result.scamType?.replace('_', ' ')?.uppercase() ?: "CRITICAL SCAM DETECTED",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SurfaceWhite,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    text = result.phoneNumber,
                    style = MaterialTheme.typography.titleLarge,
                    color = SurfaceWhite,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "On-device AASIST neural network & Kaldi speech pipeline flagged synthetic voice cloning and financial extortion patterns.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SurfaceWhite.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(Modifier.height(14.dp))
                // Immediate Safety Guidance Checklist
                Surface(
                    color = SurfaceWhite.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Immediate Actions Required:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        listOf(
                            "Do NOT transfer money or share OTP / PIN",
                            "Disconnect & block this number immediately",
                            "File a quick Cybercrime FIR (1930 / NCRP)",
                            "Dispatch emergency alert to family guardians"
                        ).forEach { action ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = SurfaceWhite.copy(alpha = 0.9f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    action,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SurfaceWhite.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                // Quick Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                HelplineAction.dial1930(context)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to launch dialer", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = BlockedRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Call 1930", color = BlockedRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            try {
                                val intent = Intent(context, GovtReportWebViewActivity::class.java).apply {
                                    putExtra(GovtReportWebViewActivity.EXTRA_CALL_ID, result.callId)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open reporting assistant", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Report Portal", color = SurfaceWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── 2. EVIDENCE DOSSIER SECTION (MATCHING APP THEME) ────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Detailed Evidence Dossier",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Surface(
                    color = RakshakSetuBlue.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "AI Verified",
                        color = RakshakSetuBlue,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // A. Acoustic & AI Forensics Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "🔍 Forensic Analysis Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = RakshakSetuBlue
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Caller Phone Number", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(result.phoneNumber, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Call Duration", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("${result.durationSec} seconds", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    HorizontalDivider(color = BorderColor)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Scam Intent Classification", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(
                                result.scamType?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Voice Clone Attack",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = BlockedRed
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Neural Threat Score", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("${(result.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = BlockedRed)
                        }
                    }

                    HorizontalDivider(color = BorderColor)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ASR Inference Latency", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("${result.pipelineMs.asr} ms (Offline Kaldi)", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("AI Acoustic Analysis", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text("AASIST GNN Synthesizer", style = MaterialTheme.typography.bodySmall, color = SafeGreen)
                        }
                    }
                }
            }

            // B. Flagged Keywords & Phrases Card
            if (result.flaggedSegments.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = BorderStroke(1.dp, BorderColor),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "🎙️ Flagged Conversation Phrases",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )

                        result.flaggedSegments.forEach { segment ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFF0F0),
                                border = BorderStroke(1.dp, Color(0xFFFFCDD2)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "“${segment.text}”",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFB71C1C),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        color = Color(0xFFB71C1C),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "${(segment.similarity * 100).toInt()}% match",
                                            color = SurfaceWhite,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // C. Auto-Generated NCRP / FIR Legal Statement Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.dp, BorderColor),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚖️ NCRP / Police Evidence Statement",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Surface(color = SafeGreenLight, shape = RoundedCornerShape(6.dp)) {
                            Text("FIR Ready", color = SafeGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = statementText,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardSetText(statementText)
                                Toast.makeText(context, "Complaint statement copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, RakshakSetuBlue)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", color = RakshakSetuBlue, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                try {
                                    val dossier = com.rakshaksetu.app.pipeline.EvidenceManager.buildDossierExport(context, result)
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Rakshak Setu Cybercrime Dossier ${result.callId.take(8)}")
                                        putExtra(Intent.EXTRA_TEXT, dossier)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share evidence dossier:"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open share sheet", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RakshakSetuBlue),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export Dossier", color = SurfaceWhite, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }

            // D. Elder Mode Guardian Alert
            if (elderEnabled) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    border = BorderStroke(1.dp, Color(0xFFFFB74D))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Elder Mode Guardian Alert", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        }
                        Text("Send immediate high-priority SMS alerts with call audio transcription to registered family guardians.", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(context, ElderAlertReceiver::class.java).apply {
                                        action = ElderAlertReceiver.ACTION_ALERT_FAMILY
                                        putExtra(ElderAlertReceiver.EXTRA_CALL_ID, result.callId)
                                        setPackage(context.packageName)
                                    }
                                    context.sendBroadcast(intent)
                                    onSnackbar("Family alert dispatched to guardians via SMS")
                                } catch (e: Exception) {
                                    onSnackbar("Could not dispatch alert: ${e.message}")
                                }
                            },
                            enabled = canAlertFamily,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.NotificationImportant, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (canAlertFamily) "DISPATCH FAMILY ALERT NOW (SMS)" else "Configure Guardians in Profile",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // E. False Positive Feedback
            OutlinedButton(
                onClick = {
                    try {
                        FeedbackLogger(context).logNotScam(result.callId, "User marked as false alarm from Evidence")
                        onSnackbar("Marked as safe. Adaptive thresholds updated.")
                        onBack()
                    } catch (e: Exception) {
                        onSnackbar("Feedback recorded")
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Icon(Icons.Default.ThumbDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Not a Threat (Report False Positive)", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}
