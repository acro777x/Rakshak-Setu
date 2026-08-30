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
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.ui.GovtReportWebViewActivity
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.data.*
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── REPORTS LIST ──────────────────────────────────────────────
@Composable
fun ReportsScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "Calls", "Links", "Files", "QR", "Images")

    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }

    val realReports = if (lastResult != null) {
        listOf(
            ReportItem(
                id = lastResult.callId,
                title = "Call from ${lastResult.phoneNumber}",
                type = "Calls",
                status = if (lastResult.isScam) RiskStatus.HIGH_RISK else RiskStatus.SAFE,
                date = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(lastResult.callEndEpoch)),
                description = if (lastResult.isScam) "${lastResult.scamType?.replace('_', ' ') ?: "Scam"} detected with ${(lastResult.confidence * 100).toInt()}% confidence. Flagged: ${lastResult.flaggedSegments.joinToString("; ") { it.text }}" else "Call verified safe. No threats detected."
            )
        )
    } else emptyList()

    val filtered = if (selectedFilter == "All") realReports
    else realReports.filter { it.type.equals(selectedFilter, ignoreCase = true) || it.type.contains(selectedFilter.dropLast(1), ignoreCase = true) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Threat Dossiers & Reports", onBackClick = onBack) },
        bottomBar = { BottomNavBar(currentRoute = Screen.Reports.route, onNavigate = onNavigate) },
        containerColor = BackgroundLight,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate(Screen.ReportStep1.route) },
                containerColor = RakshakSetuBlue,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = SurfaceWhite)
                    Text("New Incident", color = SurfaceWhite, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Filter chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                filters.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, style = MaterialTheme.typography.labelMedium) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = RakshakSetuBlue,
                            selectedLabelColor = SurfaceWhite,
                            containerColor = SurfaceWhite
                        )
                    )
                }
            }

            if (filtered.isEmpty()) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SafeGreen, modifier = Modifier.size(48.dp))
                            Text("No threats logged yet.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Analyzed calls and scans will appear here automatically.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            } else {
                filtered.forEach { report ->
                    ReportRow(report = report, onClick = { onNavigate(Screen.ReportDetails.createRoute(report.id)) })
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

@Composable
fun ReportRow(report: ReportItem, onClick: () -> Unit) {
    val icon = when (report.type.lowercase()) {
        "calls" -> Icons.Filled.Phone
        "links" -> Icons.Filled.Link
        "files" -> Icons.Filled.FileCopy
        "qr" -> Icons.Filled.QrCode
        else -> Icons.Filled.Image
    }
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(RakshakSetuBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(report.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(report.date, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            RiskBadge(report.status)
        }
    }
}

// ── REPORT DETAILS ─────────────────────────────────────────────
@Composable
fun ReportDetailsScreen(reportId: String, onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lastResult = remember { DetectionStore.getLastResult(context) }

    val report = if (lastResult != null && (reportId.isBlank() || reportId == lastResult.callId)) {
        ReportItem(
            id = lastResult.callId,
            title = "Call from ${lastResult.phoneNumber}",
            type = "Calls",
            status = if (lastResult.isScam) RiskStatus.HIGH_RISK else RiskStatus.SAFE,
            date = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(lastResult.callEndEpoch)),
            description = if (lastResult.isScam) "${lastResult.scamType?.replace('_', ' ') ?: "Scam"} detected with ${(lastResult.confidence * 100).toInt()}% confidence. ${lastResult.flaggedSegments.size} flagged segments." else "Call verified safe. No threats detected."
        )
    } else {
        ReportItem(id = "none", title = "Incident Dossier", type = "Calls", status = RiskStatus.SAFE, date = "Today", description = "No active threat dossier.")
    }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Incident Dossier", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ResultCard(
                status = report.status,
                headline = report.title,
                body = report.description
            )

            SectionCard {
                Text("Analysis Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AnalysisRow("Category", report.type, TextPrimary)
                AnalysisRow("Timestamp", report.date, TextPrimary)
                AnalysisRow("Verdict", report.status.name, when (report.status) {
                    RiskStatus.SAFE -> SafeGreen
                    RiskStatus.SUSPICIOUS -> SuspiciousAmber
                    else -> BlockedRed
                })
            }

            if (lastResult != null && lastResult.fullTranscript.isNotBlank()) {
                SectionCard {
                    Text("Captured Transcript", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(lastResult.fullTranscript, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 20.sp)
                }
            }

            PrimaryButton(
                text = "⚡ File on NCRP Portal (cybercrime.gov.in)",
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

            SecondaryButton("Guided 4-Step Report Wizard", onClick = { onNavigate(Screen.ReportStep1.route) }, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Flag)
        }
    }
}
