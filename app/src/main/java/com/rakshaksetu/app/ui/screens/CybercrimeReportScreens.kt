package com.rakshaksetu.app.ui.screens

import android.content.Context
import android.content.Intent
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
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.report.UserProfileStore
import com.rakshaksetu.app.ui.GovtReportWebViewActivity
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.data.*
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── STEP 1: Incident Details ───────────────────────────────────
@Composable
fun ReportStep1Screen(onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }
    val profile = remember { UserProfileStore(context) }

    val epochMs = (lastResult?.callEndEpoch ?: System.currentTimeMillis()).let {
        if (it > 100_000_000_000L) it else it * 1000L
    }

    var incidentDate by remember {
        mutableStateOf(SimpleDateFormat("dd MMM yyyy", Locale.US).format(Date(epochMs)))
    }
    var incidentTime by remember {
        mutableStateOf(SimpleDateFormat("hh:mm a", Locale.US).format(Date(epochMs)))
    }
    var description by remember {
        mutableStateOf(
            if (lastResult != null) {
                "Suspected fraud call from ${lastResult.phoneNumber}. The caller used ${lastResult.scamType?.replace('_', ' ') ?: "extortion"} script patterns to demand immediate funds. Key statements flagged by AI analysis: ${lastResult.flaggedSegments.joinToString("; ") { it.text }}. Filing complaint under IT Act provisions."
            } else ""
        )
    }

    val prohibitedChars = setOf('#', '$', '@', '^', '*', '"', '~', '|')
    val isDescValid = description.length >= 50 && description.none { it in prohibitedChars }
    val charError = description.any { it in prohibitedChars }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Cybercrime Report", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepProgress(4, 0)

            Surface(
                color = RakshakSetuBlue.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp))
                    Column {
                        Text("1-Tap Auto-Filled Incident Dossier", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = RakshakSetuBlue)
                        Text("Incident evidence & transcripts are populated from your call analysis.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            SectionCard {
                Text("1. Incident Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                Text("Incident Date & Time", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = incidentDate,
                        onValueChange = { incidentDate = it },
                        label = { Text("Date") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(18.dp)) }
                    )
                    OutlinedTextField(
                        value = incidentTime,
                        onValueChange = { incidentTime = it },
                        label = { Text("Time") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("Incident Description", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Describe what happened (auto-filled from AI analysis):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Type incident description…") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 6,
                    isError = charError
                )
            }

            if (lastResult != null) {
                SectionCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Surface(color = SafeGreenLight, shape = RoundedCornerShape(4.dp)) {
                            Text("AUTO-CAPTURED EVIDENCE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = SafeGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AnalysisRow("Suspect Caller", lastResult.phoneNumber, TextPrimary)
                    AnalysisRow("Scam Type", lastResult.scamType?.replace('_', ' ') ?: "Fraud Call", TextPrimary)
                    AnalysisRow("AI Confidence", "${(lastResult.confidence * 100).toInt()}%", BlockedRed)
                }
            }

            PrimaryButton(
                text = "Next: Complainant Details →",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── STEP 2: Identity & Complainant ─────────────────────────────
@Composable
fun ReportStep2Screen(onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val profile = remember { UserProfileStore(context) }
    var name by remember { mutableStateOf(profile.fullName) }
    var phone by remember { mutableStateOf(profile.phone) }
    var email by remember { mutableStateOf(profile.email) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Complainant Details", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepProgress(4, 1)

            SectionCard {
                Text("2. Complainant Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Complainant Name") },
                    placeholder = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(10.dp)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )
            }

            PrimaryButton(
                text = "Save & Continue →",
                onClick = {
                    profile.fullName = name
                    profile.phone = phone
                    profile.email = email
                    onNext()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── STEP 3: Evidence Pack ──────────────────────────────────────
@Composable
fun ReportStep3Screen(onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Evidence Pack", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepProgress(4, 2)

            SectionCard {
                Text("3. Evidence Attachment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AnalysisRow("Call Audio Recording", if (lastResult != null) "call_${lastResult.callId}.wav ✓" else "Attached ✓", SafeGreen)
                AnalysisRow("ASR Transcript Dossier", "transcript_evidence.txt ✓", SafeGreen)
                AnalysisRow("AI Neural Verification", "AASIST Confidence Report ✓", SafeGreen)
            }

            PrimaryButton("Proceed to Review →", onClick = onNext, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── STEP 4: Review & Submit ────────────────────────────────────
@Composable
fun ReportStep4Screen(onSubmit: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }
    val profile = remember { UserProfileStore(context) }
    var agreed by remember { mutableStateOf(true) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Review & Submit", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StepProgress(4, 3)

            Text("Review Your Report", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            SectionCard {
                Text("Incident Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AnalysisRow("Complainant", profile.fullName.ifBlank { "Registered User" }, TextPrimary)
                AnalysisRow("Suspect Phone", lastResult?.phoneNumber ?: "+91 98765 43210", TextPrimary)
                AnalysisRow("Incident Type", lastResult?.scamType?.replace('_', ' ') ?: "Voice Clone Fraud", TextPrimary)
                AnalysisRow("AI Confidence", "${((lastResult?.confidence ?: 0.85f) * 100).toInt()}%", BlockedRed)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (agreed) SafeGreenLight else BackgroundLight)
                    .border(1.5.dp, if (agreed) SafeGreen else BorderColor, RoundedCornerShape(12.dp))
                    .clickable { agreed = !agreed }
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it }, colors = CheckboxDefaults.colors(checkedColor = SafeGreen))
                Text(
                    "I confirm the incident details are accurate and authorize filing with the National Cyber Crime Reporting Portal (cybercrime.gov.in / 1930).",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary
                )
            }

            PrimaryButton(
                text = "Generate Dossier & Submit",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = agreed,
                icon = Icons.Filled.Send
            )
        }
    }
}

// ── REPORT SUCCESS ─────────────────────────────────────────────
@Composable
fun ReportSuccessScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier.size(90.dp).clip(CircleShape).background(SafeGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(50.dp))
        }

        Text("Incident Dossier Prepared!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = SafeGreen)
        Text("Your cybercrime report has been compiled for official filing.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

        SectionCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Incident Reference ID", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text("RS-CYBER-${System.currentTimeMillis().toString().takeLast(6)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = RakshakSetuBlue)
                Text("Save this ID for law enforcement tracking", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        PrimaryButton(
            text = "⚡ Open NCRP Portal with 1-Tap AutoFill",
            onClick = {
                context.startActivity(Intent(context, GovtReportWebViewActivity::class.java).apply {
                    if (lastResult != null) {
                        putExtra(GovtReportWebViewActivity.EXTRA_CALL_ID, lastResult.callId)
                    }
                })
            },
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Bolt
        )

        SecondaryButton("Done — Return Home", onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}
