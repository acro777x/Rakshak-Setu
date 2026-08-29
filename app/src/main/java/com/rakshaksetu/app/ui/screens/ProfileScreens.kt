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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rakshaksetu.app.ui.data.*
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.theme.*

// ── PROFILE HOME ──────────────────────────────────────────────
@Composable
fun ProfileScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = { SafeShieldTopBar(title = "Profile", onBackClick = onBack) },
        bottomBar = { BottomNavBar(currentRoute = Screen.Profile.route, onNavigate = onNavigate) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(SafeShieldBlueLight, SafeShieldBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(40.dp))
                    }
                    Text("Harsh Sharma", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("harsh@email.com  ·  +91 98765 00000", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onNavigate(Screen.PersonalInfo.route) },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SafeShieldBlue)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Profile", color = SafeShieldBlue, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Stats row
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(MockData.protectedCount, "Protected", SafeGreen, Modifier.weight(1f))
                StatCard(MockData.blockedCount, "Blocked", BlockedRed, Modifier.weight(1f))
                StatCard(MockData.filesScannedCount, "Scanned", SafeShieldBlue, Modifier.weight(1f))
            }

            // Menu sections
            SectionCard {
                Text("Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.Person, "Personal Information", Color(0xFF1565C0)) { onNavigate(Screen.PersonalInfo.route) }
                ProfileMenuItem(Icons.Filled.Shield, "Security & Privacy", Color(0xFF2E7D32)) { onNavigate(Screen.SecurityPrivacy.route) }
                ProfileMenuItem(Icons.Filled.People, "Trusted Contacts", Color(0xFF6A1B9A)) { onNavigate(Screen.TrustedContacts.route) }
                ProfileMenuItem(Icons.Filled.Bookmark, "Saved Items", Color(0xFFE65100)) { onNavigate(Screen.SavedItems.route) }
            }

            SectionCard {
                Text("Preferences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.Notifications, "Notification Settings", Color(0xFF0288D1)) { onNavigate(Screen.NotificationSettings.route) }
                ProfileMenuItem(Icons.Filled.Settings, "App Settings", Color(0xFF607D8B)) { onNavigate(Screen.Settings.route) }
            }

            SectionCard {
                Text("Support", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.Help, "Help & Support", Color(0xFF00838F)) { onNavigate(Screen.HelpSupport.route) }
                ProfileMenuItem(Icons.Filled.Info, "About SafeShield", Color(0xFF558B2F)) { onNavigate(Screen.AboutSafeShield.route) }
            }

            // Sign out
            Card(
                modifier = Modifier.fillMaxWidth().clickable {},
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = BlockedRedLight),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = null, tint = BlockedRed, modifier = Modifier.size(22.dp))
                    Text("Sign Out", style = MaterialTheme.typography.titleMedium, color = BlockedRed, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ProfileMenuItem(icon: ImageVector, label: String, iconColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = iconColor, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
    }
}

// ── PERSONAL INFO ─────────────────────────────────────────────
@Composable
fun PersonalInfoScreen(onBack: () -> Unit) {
    var name by remember { mutableStateOf("Harsh Sharma") }
    var email by remember { mutableStateOf("harsh@email.com") }
    var phone by remember { mutableStateOf("+91 98765 00000") }
    var city by remember { mutableStateOf("New Delhi") }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Personal Information", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(20.dp)) }
                )
            }
            PrimaryButton("Save Changes", onClick = onBack, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Save)
        }
    }
}

// ── TRUSTED CONTACTS ──────────────────────────────────────────
@Composable
fun TrustedContactsScreen(onBack: () -> Unit) {
    var contacts by remember { mutableStateOf(MockData.trustedContacts) }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Trusted Contacts", onBackClick = onBack) },
        containerColor = BackgroundLight,
        floatingActionButton = {
            FloatingActionButton(containerColor = SafeShieldBlue, shape = RoundedCornerShape(14.dp), onClick = {}) {
                Icon(Icons.Filled.PersonAdd, contentDescription = "Add Contact", tint = SurfaceWhite)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("People SafeShield can alert if you're in danger.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            contacts.forEach { contact ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(SafeShieldBlue.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(contact.name.first().uppercase(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = SafeShieldBlue)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(contact.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text(contact.relation, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            Text(contact.phone, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        }
                        Row {
                            IconButton(onClick = {}) { Icon(Icons.Filled.Call, contentDescription = "Call", tint = SafeGreen) }
                            IconButton(onClick = { contacts = contacts.filter { it.id != contact.id } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = BlockedRed.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── SECURITY & PRIVACY ────────────────────────────────────────
@Composable
fun SecurityPrivacyScreen(onBack: () -> Unit) {
    var biometric by remember { mutableStateOf(true) }
    var autoScan by remember { mutableStateOf(true) }
    var shareAnon by remember { mutableStateOf(false) }
    var locationData by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Security & Privacy", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard {
                Text("Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ToggleRow("Biometric / PIN Lock", "Require authentication to open SafeShield", biometric) { biometric = it }
                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                ToggleRow("Auto Scan Calls", "Automatically analyze incoming calls", autoScan) { autoScan = it }
            }
            SectionCard {
                Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ToggleRow("Share Anonymous Data", "Help improve SafeShield's detection models", shareAnon) { shareAnon = it }
                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                ToggleRow("Include Location in Reports", "Add your location to cybercrime reports", locationData) { locationData = it }
            }
            SectionCard {
                Text("Data Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.Download, "Export My Data", SafeShieldBlue) {}
                Divider(color = BorderColor)
                ProfileMenuItem(Icons.Filled.DeleteForever, "Delete All Data", BlockedRed) {}
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceWhite, checkedTrackColor = SafeShieldBlue)
        )
    }
}

// ── NOTIFICATION SETTINGS ─────────────────────────────────────
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val settings = remember {
        mutableStateListOf(
            Triple("Red Alert Notifications", "Critical threat detected", true),
            Triple("Yellow Alert Notifications", "Suspicious activity detected", true),
            Triple("Scan Results", "When a scan finishes", true),
            Triple("Daily Safety Tips", "Educational notifications", false),
            Triple("SafeShield Updates", "App and database updates", true)
        )
    }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Notifications", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionCard {
                settings.forEachIndexed { i, (title, sub, checked) ->
                    ToggleRow(title, sub, checked) { settings[i] = Triple(title, sub, it) }
                    if (i < settings.lastIndex) Divider(color = BorderColor, modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

// ── HELP & SUPPORT ────────────────────────────────────────────
@Composable
fun HelpSupportScreen(onBack: () -> Unit) {
    val faqs = listOf(
        "How does call analysis work?" to "SafeShield uses local AI models to detect synthetic voice patterns and scam scripts.",
        "Is my data stored on a server?" to "No. All analysis in this demo runs entirely on-device. No data is transmitted.",
        "What is a Red Alert?" to "A Red Alert means a confirmed high-risk threat was detected. Take immediate action.",
        "How do I report a cybercrime?" to "Use the Reports tab → New Report to file a 4-step cybercrime incident report.",
        "Can I trust the scan results?" to "Results are probabilistic indicators. Always verify with official channels for critical decisions."
    )

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Help & Support", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Quick contact
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf(Icons.Filled.Call to "1930", Icons.Filled.Email to "Email Us", Icons.Filled.Chat to "Live Chat").forEach { (icon, label) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable {}) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SafeShieldBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Icon(icon, contentDescription = label, tint = SafeShieldBlue, modifier = Modifier.size(22.dp))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(label, style = MaterialTheme.typography.labelSmall, color = SafeShieldBlue, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Text("FAQs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            faqs.forEach { (q, a) ->
                var expanded by remember { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(q, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = TextSecondary)
                        }
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            Text(a, style = MaterialTheme.typography.bodySmall, color = TextSecondary, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

// ── ABOUT ─────────────────────────────────────────────────────
@Composable
fun AboutSafeShieldScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { SafeShieldTopBar(title = "About SafeShield", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = SafeShieldBlue, modifier = Modifier.size(64.dp))
            Text("SafeShield", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = SafeShieldBlue)
            Text("v1.0.0 — Frontend Demo", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Text("Your Digital Safety Companion", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            SectionCard {
                listOf("Build" to "1.0.0-demo", "Platform" to "Android (Jetpack Compose)", "Design System" to "Material 3", "License" to "Demo / Prototype").forEach { (k, v) ->
                    AnalysisRow(k, v)
                }
            }

            Text(
                "SafeShield is a frontend-only prototype built to demonstrate how digital safety features can be packaged into a beautiful, intuitive Android experience.\n\nAll scan results are simulated. No real data is collected or transmitted.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 20.sp
            )
        }
    }
}

// ── SETTINGS ─────────────────────────────────────────────────
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var darkMode by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("English") }
    var fontSize by remember { mutableStateOf("Medium") }

    Scaffold(
        topBar = { SafeShieldTopBar(title = "Settings", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard {
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ToggleRow("Dark Mode", "Use dark theme", darkMode) { darkMode = it }
                Divider(color = BorderColor, modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Language", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(language, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                    OutlinedButton(onClick = { language = if (language == "English") "Hindi" else "English" }, shape = RoundedCornerShape(8.dp)) {
                        Text("Change", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            SectionCard {
                Text("Cache & Storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                AnalysisRow("App Cache", "24 MB")
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Clear Cache", onClick = {}, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

// ── SAVED ITEMS ───────────────────────────────────────────────
@Composable
fun SavedItemsScreen(onBack: () -> Unit) {
    val items = listOf("Call: +91 98765 43210" to "High Risk", "Link: https://secure.nmrc.in" to "Safe", "File: invoice.pdf" to "Safe")
    Scaffold(
        topBar = { SafeShieldTopBar(title = "Saved Items", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (items.isEmpty()) {
                EmptyState("No saved items yet", Icons.Filled.BookmarkBorder)
            } else {
                items.forEach { (title, status) ->
                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(status, style = MaterialTheme.typography.labelSmall, color = if (status == "Safe") SafeGreen else BlockedRed)
                            }
                            Icon(Icons.Filled.Bookmark, contentDescription = null, tint = SafeShieldBlue)
                        }
                    }
                }
            }
        }
    }
}
