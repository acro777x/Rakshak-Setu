package com.rakshaksetu.app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rakshaksetu.app.ui.data.*
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.theme.*

// ── STEP 1: Incident Details ───────────────────────────────────
@Composable
fun ReportStep1Screen(onNext: () -> Unit, onBack: () -> Unit) {
    var description by remember { mutableStateOf("") }
    var incidentDate by remember { mutableStateOf("21 May 2025") }
    var incidentTime by remember { mutableStateOf("10:30 AM") }
    val prohibitedChars = setOf('#', '$', '@', '^', '*', '"', '~', '|')
    val isDescValid = description.length >= 200 && description.none { it in prohibitedChars }
    val charError = description.any { it in prohibitedChars }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Report Profile", onBackClick = onBack) },
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
                        Text("Please provide the following information manually.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = RakshakSetuBlue)
                        Text("Other details will be collected automatically where possible.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }

            SectionCard {
                Text("1. Incident Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))

                Text("Incident Date & Time *", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
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
                Text("Incident Description *", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Please describe what happened in detail (minimum 200 characters).", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("Type your incident description here…") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(10.dp),
                    maxLines = 6,
                    isError = charError
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (charError) {
                        Text("Special characters # \$ @ ^ * \" ~ | not allowed", style = MaterialTheme.typography.labelSmall, color = BlockedRed)
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Text("${description.length} / 200", style = MaterialTheme.typography.labelSmall,
                        color = if (description.length >= 200) SafeGreen else TextSecondary)
                }
            }

            SectionCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Surface(color = SafeGreenLight, shape = RoundedCornerShape(4.dp)) {
                        Text("AUTO-DETECTED", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = SafeGreen, fontWeight = FontWeight.Bold)
                    }
                    Text("The following info was captured from your scan", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                Spacer(Modifier.height(10.dp))
                AnalysisRow("Caller Number", "+91 98765 43210", TextPrimary)
                AnalysisRow("Scan Date", "21 May 2025, 10:30 AM", TextPrimary)
                AnalysisRow("Incident Type", "Voice Clone / Call Fraud", TextPrimary)
                AnalysisRow("AI Confidence", "82%", BlockedRed)
            }

            PrimaryButton(
                text = "Save & Continue →",
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                enabled = isDescValid
            )

            if (!isDescValid && description.isNotEmpty()) {
                Text(
                    if (charError) "Remove special characters to continue."
                    else "Description needs at least ${200 - description.length} more characters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SuspiciousAmber
                )
            }
        }
    }
}

// ── STEP 2: Identity & Evidence ────────────────────────────────
@Composable
fun ReportStep2Screen(onNext: () -> Unit, onBack: () -> Unit) {
    var idFile by remember { mutableStateOf<String?>(null) }
    var evidenceFile by remember { mutableStateOf<String?>(null) }
    var isFinancialFraud by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Report Profile", onBackClick = onBack) },
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
                Text("2. Your Identity (ID Proof)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Upload any one of the following: Voter ID, Driving License, Passport, PAN Card, Aadhaar Card", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                UploadCard(
                    title = "Upload ID Proof",
                    subtitle = "",
                    selectedFileName = idFile,
                    selectedFileSize = "1.2 MB",
                    acceptedTypes = ".jpeg, .jpg, .png",
                    maxSize = "5 MB",
                    onSelectClick = { idFile = "id_proof.jpg" },
                    onRemoveClick = { idFile = null }
                )
            }

            SectionCard {
                Text("3. Relevant Evidence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Upload any evidence related to this incident (screenshots, chats, documents, etc.)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                UploadCard(
                    title = "Upload Evidence",
                    subtitle = "",
                    selectedFileName = evidenceFile,
                    selectedFileSize = "2.4 MB",
                    acceptedTypes = ".jpeg, .jpg, .png",
                    maxSize = "10 MB each",
                    onSelectClick = { evidenceFile = "screenshot_evidence.png" },
                    onRemoveClick = { evidenceFile = null }
                )
            }

            // Financial fraud toggle
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = BorderStroke(1.5.dp, BorderColor),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Was money lost in this incident?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Include financial fraud details in the report", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    Switch(
                        checked = isFinancialFraud,
                        onCheckedChange = { isFinancialFraud = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = RakshakSetuBlue, checkedTrackColor = RakshakSetuBlueLight)
                    )
                }
            }

            PrimaryButton("Continue →", onClick = { if (isFinancialFraud) onNext() else onNext() }, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── STEP 3: Financial Details ──────────────────────────────────
@Composable
fun ReportStep3Screen(onNext: () -> Unit, onBack: () -> Unit) {
    var bankName by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("") }
    var transactionDate by remember { mutableStateOf("21 May 2025") }
    var amount by remember { mutableStateOf("") }
    val transactionError = transactionId.isNotEmpty() && (transactionId.length != 12 || !transactionId.all { it.isDigit() })

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Report Profile", onBackClick = onBack) },
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

            Text("Financial Fraud Details", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("If applicable to your incident. Leave blank if no money was lost.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            SectionCard {
                OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Bank / Wallet / Merchant") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = transactionId,
                    onValueChange = { if (it.length <= 12) transactionId = it },
                    label = { Text("12-digit Transaction ID / UTR") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    isError = transactionError,
                    supportingText = {
                        if (transactionError) Text("Must be exactly 12 digits", color = BlockedRed)
                        else Text("${transactionId.length}/12 digits", color = TextSecondary)
                    }
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = transactionDate, onValueChange = { transactionDate = it }, label = { Text("Transaction Date") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Fraud Amount (₹)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp))
            }

            // Optional suspect info
            SectionCard {
                Text("Optional / Desirable Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RakshakSetuBlue)
                Spacer(Modifier.height(8.dp))
                OptionalFieldRow("4. Suspect Details (if known)", "Add any details you know about the suspect.") {}
                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 8.dp))
                OptionalFieldRow("5. Additional Information (Optional)", "Add any other information that you think might help.") {}
            }

            PrimaryButton("Continue →", onClick = onNext, modifier = Modifier.fillMaxWidth(), enabled = !transactionError)
        }
    }
}

@Composable
fun OptionalFieldRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
    }
}

// ── STEP 4: Review & Submit ────────────────────────────────────
@Composable
fun ReportStep4Screen(onSubmit: () -> Unit, onBack: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }

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
            Text("Please review all details before submission.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            SectionCard {
                Text("Incident Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AnalysisRow("Date & Time", "21 May 2025, 10:30 AM", TextPrimary)
                AnalysisRow("Incident Type", "Voice Clone / Call Fraud", TextPrimary)
                AnalysisRow("Caller", "+91 98765 43210", TextPrimary)
                AnalysisRow("AI Risk Score", "82% — High Risk", BlockedRed)
                AnalysisRow("ID Proof", "id_proof.jpg ✓", SafeGreen)
                AnalysisRow("Evidence", "screenshot_evidence.png ✓", SafeGreen)
            }

            SectionCard {
                Text("Auto-filled by Rakshak Setu", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RakshakSetuBlue)
                Spacer(Modifier.height(8.dp))
                AnalysisRow("App Version", "Rakshak Setu v1.0", TextPrimary)
                AnalysisRow("Device", "Android (Demo)", TextPrimary)
                AnalysisRow("Scan Timestamp", "21 May 2025, 10:30 AM", TextPrimary)
                AnalysisRow("Analysis ID", "SS-SCAN-20250521-1030", TextPrimary)
            }

            // Consent checkbox
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
                    "I confirm that the information provided is accurate to the best of my knowledge. I consent to this information being used for the purpose of filing a cybercrime incident report.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    lineHeight = 20.sp
                )
            }

            PrimaryButton(
                text = "Submit Report",
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(40.dp))

        Box(
            modifier = Modifier.size(100.dp).clip(CircleShape).background(SafeGreenLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(56.dp))
        }

        Text("Report Submitted!", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = SafeGreen)
        Text("Your incident report has been recorded.", style = MaterialTheme.typography.bodyLarge, color = TextSecondary)

        SectionCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Reference ID", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                Text(MockData.mockReferenceId, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = RakshakSetuBlue)
                Text("Save this ID for follow-up", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }

        SectionCard {
            Text("Next Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            listOf(
                "Contact your bank to freeze suspicious transactions",
                "Call the cybercrime helpline at 1930",
                "Block the caller's number from your phone settings",
                "Keep all evidence safe — do not delete messages or files"
            ).forEachIndexed { index, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = RakshakSetuBlue)
                    Text(step, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
        }

        PrimaryButton("Done — Return Home", onClick = onDone, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Home)
    }
}
