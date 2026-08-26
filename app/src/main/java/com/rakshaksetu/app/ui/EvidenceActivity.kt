package com.rakshaksetu.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.app.action.BankEmailAction
import com.rakshaksetu.app.action.GovtPortalAction
import com.rakshaksetu.app.action.HelplineAction
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.elder.ElderModeStore
import com.rakshaksetu.app.elder.EmergencyDispatcher
import com.rakshaksetu.app.elder.RakshakAppTheme
import com.rakshaksetu.app.evidence.StatementGenerator
import com.rakshaksetu.app.feedback.FeedbackLogger
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.receiver.ElderAlertReceiver
import kotlinx.coroutines.launch

class EvidenceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra(ScamAlertManager.EXTRA_CALL_ID)
            ?: intent.getStringExtra("CALL_ID")
            ?: "unknown"
        val elderMode = ElderModeStore(applicationContext).isEnabled

        setContent {
            RakshakAppTheme(elderModeEnabled = elderMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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

    // Honest empty-state: no fabricated dossiers in production builds.
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
                title = { Text("Scam Evidence Dossier", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text("No evidence available for this call", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Either the call recording could not be located, or the analysis has not run yet. " +
                "Rakshak Setu never invents evidence.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onBack) { Text("Go Back") }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Threat Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "⚠️ ${result.scamType?.replace("_", " ")?.uppercase() ?: "SCAM DETECTED"}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Badge(containerColor = Color.White) {
                        Text(
                            text = "${(result.confidence * 100).toInt()}% RISK",
                            color = Color(0xFFB71C1C),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Caller: ${result.phoneNumber} • Duration: ${result.durationSec}s",
                    color = Color(0xFFFFCDD2),
                    fontSize = 14.sp
                )
                Text(
                    text = "Action required: Do not transfer money or enter OTPs.",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 2. Golden Hour Actions
        Text("Emergency Actions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Call 1930", fontSize = 13.sp)
            }

            Button(
                onClick = {
                    try {
                        val bank = BankEmailAction.getBanks().firstOrNull() ?: BankEmailAction.findBankByName("SBI")
                        if (bank != null) {
                            val intent = BankEmailAction.buildEmailIntent(result, bank)
                            context.startActivity(intent)
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "No email client found", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Bank Freeze", fontSize = 13.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Report to Govt", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
            }

            OutlinedButton(
                onClick = {
                    try {
                        GovtPortalAction.openChakshu(context)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Chakshu", fontSize = 12.sp)
            }
        }

        // 3. Elder Mode one-tap family alert
        if (elderEnabled) {
            Button(
                onClick = {
                    try {
                        val intent = Intent(context, ElderAlertReceiver::class.java).apply {
                            action = ElderAlertReceiver.ACTION_ALERT_FAMILY
                            putExtra(ElderAlertReceiver.EXTRA_CALL_ID, result.callId)
                            setPackage(context.packageName)
                        }
                        context.sendBroadcast(intent)
                        onSnackbar("Family alert request sent to guardians")
                    } catch (e: Exception) {
                        onSnackbar("Could not dispatch family alert: ${e.message}")
                    }
                },
                enabled = canAlertFamily,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF6D00),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF424242)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.NotificationImportant, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (canAlertFamily) "ALERT FAMILY NOW (SMS)"
                    else if (!elderEnabled) "Elder Mode off"
                    else "No guardians configured — set them in the app",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 4. Flagged Phrases
        if (result.flaggedSegments.isNotEmpty()) {
            Text("Flagged Keywords & Phrases", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    result.flaggedSegments.forEach { segment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF332020), RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "“${segment.text}”",
                                color = Color(0xFFFF8A80),
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(segment.similarity * 100).toInt()}% match",
                                color = Color(0xFFFFB300),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                "No exact phrase matches — verdict driven by acoustic risk analysis (voice-clone artifacts, stress, environment).",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 5. Complaint Statement
        Text("Auto-Generated NCRP Complaint Statement", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = statementText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            clipboardSetText(statementText)
                            Toast.makeText(context, "Complaint statement copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Statement")
                    }

                    Button(
                        onClick = {
                            try {
                                val dossier = com.rakshaksetu.app.pipeline.EvidenceManager.buildDossierExport(context, result)
                                val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        android.content.Intent.EXTRA_SUBJECT,
                                        "Rakshak Setu Evidence Dossier ${result.callId.take(8)}"
                                    )
                                    putExtra(android.content.Intent.EXTRA_TEXT, dossier)
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(sendIntent, "Share evidence dossier with:")
                                )
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open share sheet: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export Dossier", maxLines = 1)
                    }
                }
            }
        }

        // 6. False Positive feedback
        OutlinedButton(
            onClick = {
                try {
                    FeedbackLogger(context).logNotScam(result.callId, "User marked as false alarm from Evidence")
                    onSnackbar("Marked as false alarm. Thresholds adapted.")
                    onBack()
                } catch (e: Exception) {
                    onSnackbar("Feedback recorded")
                    onBack()
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Not a Scam (Report False Alarm)")
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
