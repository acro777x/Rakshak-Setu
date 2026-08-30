package com.rakshaksetu.app.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.consent.ConsentStore

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val consentStore = remember { ConsentStore(context) }
    val isShieldActive = consentStore.isShieldActive
    val lastResult = remember { DetectionStore.getLastResult(context) }
    
    // Compute live stats from DetectionStore
    val safeCount = if (lastResult != null && !lastResult.isScam) 1 else 0
    val suspiciousCount = if (lastResult != null && lastResult.isScam && lastResult.confidence < 0.7f) 1 else 0
    val blockedCount = if (lastResult != null && lastResult.isScam && lastResult.confidence >= 0.7f) 1 else 0
    val totalScanned = if (lastResult != null) 1 else 0
    
    // Convert real DetectionResult to ActivityItem for the UI
    val recentActivity = if (lastResult != null) {
        listOf(
            com.rakshaksetu.app.ui.data.ActivityItem(
                id = lastResult.callId,
                title = "Call from ${lastResult.phoneNumber}",
                subtitle = if (lastResult.isScam) "${lastResult.scamType ?: "Scam"} detected (${(lastResult.confidence * 100).toInt()}%)" else "Call verified safe",
                type = "call",
                status = if (lastResult.isScam) com.rakshaksetu.app.ui.data.RiskStatus.HIGH_RISK else com.rakshaksetu.app.ui.data.RiskStatus.SAFE,
                timestamp = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(lastResult.callEndEpoch))
            )
        )
    } else emptyList()

    Scaffold(
        topBar = {
            RakshakSetuTopBar(
                title = "Rakshak Setu",
                onMenuClick = {},
                onNotificationClick = { onNavigate(Screen.AlertCenter.route) },
                onProfileClick = { onNavigate(Screen.Profile.route) }
            )
        },
        bottomBar = { BottomNavBar(currentRoute = Screen.Dashboard.route, onNavigate = onNavigate) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── HERO CARD ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF1565C0), Color(0xFF1E88E5))
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(20.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Stay Safe.\nStay Ahead. 🛡",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = SurfaceWhite
                            )
                            Text(
                                "Scan calls, links, files — stay\none step ahead of fraud.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SurfaceWhite.copy(alpha = 0.85f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { onNavigate(Screen.ScanHub.route) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite)
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run Smart Scan", color = RakshakSetuBlue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                        Icon(
                            Icons.Filled.Shield,
                            contentDescription = null,
                            tint = SurfaceWhite.copy(alpha = 0.25f),
                            modifier = Modifier.size(80.dp)
                        )
                    }
                }
            }

            // ── PROTECTION STATS ───────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(MockData.protectedCount, "Safe", SafeGreen, Modifier.weight(1f))
                StatCard(MockData.suspiciousCount, "Suspicious", SuspiciousAmber, Modifier.weight(1f))
                StatCard(MockData.blockedCount, "Blocked", BlockedRed, Modifier.weight(1f))
                StatCard(MockData.filesScannedCount, "Files", RakshakSetuBlue, Modifier.weight(1f))
            }

            // ── QUICK ACTIONS ──────────────────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Quick Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("See All", style = MaterialTheme.typography.labelMedium, color = RakshakSetuBlue,
                        modifier = Modifier.clickable { onNavigate(Screen.ScanHub.route) })
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    QuickActionCard("Call\nSecurity", Icons.Filled.Phone, Color(0xFFE3F2FD), RakshakSetuBlue) { onNavigate(Screen.CallSecurity.route) }
                    QuickActionCard("Link\nChecker", Icons.Filled.Link, Color(0xFFE8F5E9), SafeGreen) { onNavigate(Screen.LinkChecker.route) }
                    QuickActionCard("QR\nScanner", Icons.Filled.QrCodeScanner, Color(0xFFF3E5F5), AIPurple) { onNavigate(Screen.QRScanner.route) }
                    QuickActionCard("File\nScanner", Icons.Filled.FileCopy, Color(0xFFFFF3E0), SuspiciousAmber) { onNavigate(Screen.FileScanner.route) }
                }
            }

            // ── TRUSTED GOVERNMENT SERVICES ────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trusted Government & Safety Services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("View All", style = MaterialTheme.typography.labelMedium, color = RakshakSetuBlue)
                }
                Spacer(Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrustedServiceChip("NMRC Portal", Icons.Filled.Gavel, "Open ↗", Modifier.weight(1f)) {}
                        TrustedServiceChip("Bank Mail", Icons.Filled.Mail, "Open ↗", Modifier.weight(1f)) {}
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TrustedServiceChip("Chakshu", Icons.Filled.RemoveRedEye, "Open ↗", Modifier.weight(1f)) {}
                        TrustedServiceChip("Call 1930", Icons.Filled.Call, "Call Now", Modifier.weight(1f)) {}
                    }
                }
            }

            // ── PROTECTION STATUS BANNER ────────────────────
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isShieldActive) SafeGreenLight else Color(0xFFFFF3E0)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isShieldActive) Icons.Filled.VerifiedUser else Icons.Filled.Warning, 
                        contentDescription = null, 
                        tint = if (isShieldActive) SafeGreen else SuspiciousAmber, 
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            if (isShieldActive) "Shield Active — You are protected" else "Shield Paused — Protection disabled", 
                            style = MaterialTheme.typography.titleMedium, 
                            fontWeight = FontWeight.Bold, 
                            color = if (isShieldActive) SafeGreen else SuspiciousAmber
                        )
                        Text(
                            if (isShieldActive) "Keep scanning, keep safe!" else "Enable shield to stay protected", 
                            style = MaterialTheme.typography.bodySmall, 
                            color = TextSecondary
                        )
                    }
                }
            }

            // ── RECENT ACTIVITY ────────────────────────────
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("View All", style = MaterialTheme.typography.labelMedium, color = RakshakSetuBlue,
                        modifier = Modifier.clickable { onNavigate(Screen.Reports.route) })
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (recentActivity.isEmpty()) {
                        Text("No calls analyzed yet. Your call analysis results will appear here.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    } else {
                        recentActivity.forEach { item ->
                            ActivityRow(item = item, onClick = { onNavigate(Screen.Reports.route) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun TrustedServiceChip(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

@Composable
fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    val icon = when (item.type) {
        "call" -> Icons.Filled.Phone
        "link" -> Icons.Filled.Link
        "qr" -> Icons.Filled.QrCode
        "file" -> Icons.Filled.FileCopy
        else -> Icons.Filled.Image
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RakshakSetuBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            RiskBadge(item.status)
            Spacer(Modifier.height(2.dp))
            Text(item.timestamp, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
    }
}

// ── BOTTOM NAVIGATION BAR ──────────────────────────────────────
@Composable
fun BottomNavBar(currentRoute: String, onNavigate: (String) -> Unit) {
    NavigationBar(
        containerColor = SurfaceWhite,
        tonalElevation = 4.dp
    ) {
        val items = listOf(
            Triple("Home", Icons.Filled.Home, Screen.Dashboard.route),
            Triple("Scan", Icons.Filled.Search, Screen.ScanHub.route),
            Triple("Shield", Icons.Filled.Shield, Screen.Dashboard.route),
            Triple("Reports", Icons.Filled.Assessment, Screen.Reports.route),
            Triple("Profile", Icons.Filled.Person, Screen.Profile.route),
        )
        items.forEachIndexed { index, (label, icon, route) ->
            NavigationBarItem(
                selected = currentRoute == route,
                onClick = { onNavigate(route) },
                icon = {
                    if (index == 2) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(RakshakSetuBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = label, tint = SurfaceWhite, modifier = Modifier.size(26.dp))
                        }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { if (index != 2) Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RakshakSetuBlue,
                    selectedTextColor = RakshakSetuBlue,
                    unselectedIconColor = NavUnselected,
                    unselectedTextColor = NavUnselected,
                    indicatorColor = RakshakSetuBlue.copy(alpha = 0.1f)
                )
            )
        }
    }
}
