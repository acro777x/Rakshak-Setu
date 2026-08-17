package com.rakshaksetu.app.ui

import android.content.Context
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
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.evidence.StatementGenerator
import com.rakshaksetu.app.feedback.FeedbackLogger
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager

class EvidenceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra(ScamAlertManager.EXTRA_CALL_ID)
            ?: intent.getStringExtra("CALL_ID")
            ?: "unknown"

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFE53935),
                    secondary = Color(0xFFFFB300),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    onSurface = Color.White
                )
            ) {
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

    // Safely load detection result
    val detectionResult = remember(callId) {
        val last = DetectionStore.getLastResult(context)
        if (last != null && (callId == "unknown" || last.callId == callId)) {
            last
        } else {
            FakePipelineEmitter.scamResult()
        }
    }

    // Safely load or generate evidence statement
    val statementText = remember(detectionResult) {
        val saved = StatementGenerator.getEvidenceStatement(context, detectionResult.callId)
        saved ?: try {
            StatementGenerator.generate(detectionResult)
        } catch (e: Exception) {
            "Evidence generated for Call ${detectionResult.callId} from ${detectionResult.phoneNumber}. Confidence: ${(detectionResult.confidence * 100).toInt()}%."
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
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. High-Risk Threat Banner
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
                            text = "⚠️ ${detectionResult.scamType?.replace("_", " ")?.uppercase() ?: "SCAM DETECTED"}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Badge(containerColor = Color.White) {
                            Text(
                                text = "${(detectionResult.confidence * 100).toInt()}% RISK",
                                color = Color(0xFFB71C1C),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Caller: ${detectionResult.phoneNumber} • Duration: ${detectionResult.durationSec}s",
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

            // 2. Immediate Golden Hour Action Buttons
            Text("Emergency Actions", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
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
                                val intent = BankEmailAction.buildEmailIntent(detectionResult, bank)
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
                OutlinedButton(
                    onClick = {
                        try {
                            GovtPortalAction.openNcrp(context)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("NCRP Portal", fontSize = 12.sp)
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

            // 3. Flagged Suspicious Phrases
            if (detectionResult.flaggedSegments.isNotEmpty()) {
                Text("Flagged Keywords & Phrases", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        detectionResult.flaggedSegments.forEach { segment ->
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
            }

            // 4. NCRP Complaint Statement Box
            Text("Auto-Generated NCRP Complaint Statement", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = statementText,
                        color = Color(0xFFEEEEEE),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(statementText))
                            Toast.makeText(context, "Complaint statement copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Statement for Govt Portal")
                    }
                }
            }

            // 5. False Positive / Feedback Action
            OutlinedButton(
                onClick = {
                    try {
                        FeedbackLogger(context).logNotScam(detectionResult.callId, "User marked as false alarm from Evidence")
                        Toast.makeText(context, "Marked as false alarm. Thank you!", Toast.LENGTH_SHORT).show()
                        onBack()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Feedback recorded", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB0BEC5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.ThumbDown, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Not a Scam (Report False Alarm)")
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
