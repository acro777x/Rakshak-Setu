package com.rakshaksetu.app.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rakshaksetu.app.ui.data.RiskStatus
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*

// ── TOP APP BAR ──────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RakshakSetuTopBar(
    title: String = "Rakshak Setu",
    onMenuClick: (() -> Unit)? = null,
    onNotificationClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = RakshakSetuBlue
            )
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
            } else if (onMenuClick != null) {
                IconButton(onClick = onMenuClick) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = TextPrimary)
                }
            }
        },
        actions = {
            if (onNotificationClick != null) {
                IconButton(onClick = onNotificationClick) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = TextPrimary)
                }
            }
            if (onProfileClick != null) {
                IconButton(onClick = onProfileClick) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = "Profile", tint = RakshakSetuBlue)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SurfaceWhite
        )
    )
}

// ── BOTTOM NAVIGATION BAR (YUGANSH 5-ITEM WITH RAISED SHIELD) ──
@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    NavigationBar(
        containerColor = SurfaceWhite,
        tonalElevation = 4.dp
    ) {
        val items = listOf(
            Triple("Home", Icons.Filled.Home, Screen.Dashboard.route),
            Triple("Scan", Icons.Filled.Search, Screen.ScanHub.route),
            Triple("Shield", Icons.Filled.Shield, Screen.Dashboard.route),
            Triple("Reports", Icons.Filled.Assessment, Screen.Reports.route),
            Triple("Profile", Icons.Filled.Person, Screen.Profile.route)
        )
        items.forEachIndexed { index, (label, icon, route) ->
            NavigationBarItem(
                selected = currentRoute == route && index != 2,
                onClick = { onNavigate(route) },
                icon = {
                    if (index == 2) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(RakshakSetuBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = label, tint = SurfaceWhite, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { if (index != 2) Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = RakshakSetuBlue,
                    selectedTextColor = RakshakSetuBlue,
                    unselectedIconColor = Color(0xFF757575),
                    unselectedTextColor = Color(0xFF757575),
                    indicatorColor = RakshakSetuBlue.copy(alpha = 0.1f)
                )
            )
        }
    }
}

// ── ANALYSIS ROW ──────────────────────────────────────────────
@Composable
fun AnalysisRow(
    label: String,
    value: String,
    valueColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

// ── VOICE WAVEFORM ANIMATION ──────────────────────────────────
@Composable
fun VoiceWaveformAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heights = (0..9).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 8f,
            targetValue = 32f,
            animationSpec = infiniteRepeatable(
                animation = tween(400 + i * 80, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "h$i"
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .width(4.dp)
                    .height(h.value.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RakshakSetuBlue)
            )
        }
    }
}

// ── RISK BADGE ───────────────────────────────────────────────
@Composable
fun RiskBadge(status: RiskStatus) {
    val (label, bg, fg) = when (status) {
        RiskStatus.SAFE -> Triple("Safe", SafeGreenLight, SafeGreen)
        RiskStatus.SUSPICIOUS -> Triple("Suspicious", SuspiciousAmberLight, SuspiciousAmber)
        RiskStatus.BLOCKED -> Triple("Blocked", BlockedRedLight, BlockedRed)
        RiskStatus.HIGH_RISK -> Triple("High Risk", BlockedRedLight, BlockedRed)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(50),
        modifier = Modifier.wrapContentSize()
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ── PRIMARY BUTTON ───────────────────────────────────────────
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = RakshakSetuBlue)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = SurfaceWhite)
    }
}

// ── SECONDARY BUTTON ─────────────────────────────────────────
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, RakshakSetuBlue)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = RakshakSetuBlue, style = MaterialTheme.typography.titleMedium)
    }
}

// ── SECTION CARD ─────────────────────────────────────────────
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

// ── QUICK ACTION CARD ────────────────────────────────────────
@Composable
fun QuickActionCard(
    label: String,
    icon: ImageVector,
    bgColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ── STAT CARD ────────────────────────────────────────────────
@Composable
fun StatCard(
    count: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

// ── STEP PROGRESS ────────────────────────────────────────────
@Composable
fun StepProgress(totalSteps: Int, currentStep: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .height(4.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            index < currentStep -> RakshakSetuBlue
                            index == currentStep -> RakshakSetuBlueLight
                            else -> BorderColor
                        }
                    )
            )
        }
    }
}

// ── SCAN RADAR ANIMATION ─────────────────────────────────────
@Composable
fun ScanRadarAnimation(color: Color = RakshakSetuBlue) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )
    Box(
        modifier = Modifier
            .size(120.dp)
            .scale(pulse),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color.copy(alpha = 0.1f))
            drawCircle(color = color.copy(alpha = 0.2f), radius = size.minDimension * 0.35f)
            drawCircle(color = color, radius = size.minDimension * 0.15f)
        }
        Icon(
            Icons.Filled.Shield,
            contentDescription = "Scanning",
            tint = color,
            modifier = Modifier
                .size(40.dp)
                .rotate(rotation)
        )
    }
}

// ── UPLOAD CARD ──────────────────────────────────────────────
@Composable
fun UploadCard(
    title: String,
    subtitle: String,
    selectedFileName: String?,
    selectedFileSize: String?,
    acceptedTypes: String,
    maxSize: String,
    onSelectClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = BorderStroke(1.5.dp, if (selectedFileName != null) RakshakSetuBlue else BorderColor),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            if (selectedFileName != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SafeGreenLight)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = selectedFileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextPrimary)
                        if (selectedFileSize != null) {
                            Text(text = selectedFileSize, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                    }
                    IconButton(onClick = onRemoveClick) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove file", tint = BlockedRed)
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BackgroundLight)
                        .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(text = "Tap to upload", style = MaterialTheme.typography.bodyMedium, color = RakshakSetuBlue, fontWeight = FontWeight.SemiBold)
                        Text(text = "$acceptedTypes  ·  Max $maxSize", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

// ── EMPTY STATE ──────────────────────────────────────────────
@Composable
fun EmptyState(message: String, icon: ImageVector = Icons.Filled.SearchOff) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = BorderColor)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

// ── RESULT CARD ──────────────────────────────────────────────
@Composable
fun ResultCard(
    status: RiskStatus,
    headline: String,
    body: String,
    modifier: Modifier = Modifier
) {
    val (bg, icon, iconTint) = when (status) {
        RiskStatus.SAFE -> Triple(SafeGreenLight, Icons.Filled.CheckCircle, SafeGreen)
        RiskStatus.SUSPICIOUS -> Triple(SuspiciousAmberLight, Icons.Filled.Warning, SuspiciousAmber)
        RiskStatus.BLOCKED, RiskStatus.HIGH_RISK -> Triple(BlockedRedLight, Icons.Filled.Error, BlockedRed)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(28.dp))
            Column {
                Text(headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
    }
}
