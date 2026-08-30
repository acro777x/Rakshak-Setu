package com.rakshaksetu.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.rakshaksetu.app.service.BatteryOptimizationHelper
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.elder.ElderModeStore
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.report.UserProfileStore
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.data.*
import com.rakshaksetu.app.ui.navigation.Screen
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.launch

// ── PROFILE HOME ──────────────────────────────────────────────
@Composable
fun ProfileScreen(onNavigate: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val profileStore = remember { UserProfileStore(context) }
    val elderStore = remember { ElderModeStore(context) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Profile & Settings", onBackClick = onBack) },
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
                            .background(Brush.radialGradient(listOf(RakshakSetuBlueLight, RakshakSetuBlue))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(40.dp))
                    }
                    Text(
                        text = profileStore.fullName.ifBlank { "Rakshak User" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${profileStore.email.ifBlank { "user@rakshaksetu.in" }}  ·  ${profileStore.phone.ifBlank { "+91 Registered" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = { onNavigate(Screen.PersonalInfo.route) },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, RakshakSetuBlue)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Complainant Profile", color = RakshakSetuBlue, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Stats row
            val lastResult = remember { DetectionStore.getLastResult(context) }
            val safeCount = if (lastResult != null && !lastResult.isScam) 1 else 0
            val blockedCount = if (lastResult != null && lastResult.isScam) 1 else 0
            val totalScanned = if (lastResult != null) 1 else 0

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(safeCount, "Protected", SafeGreen, Modifier.weight(1f))
                StatCard(blockedCount, "Threats", BlockedRed, Modifier.weight(1f))
                StatCard(totalScanned, "Analyses", RakshakSetuBlue, Modifier.weight(1f))
            }

            // Menu sections
            SectionCard {
                Text("Account & Emergency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.Person, "Complainant Details (NCRP/Chakshu)", Color(0xFF1565C0)) { onNavigate(Screen.PersonalInfo.route) }
                ProfileMenuItem(Icons.Filled.Shield, "Security, Shield & Privacy", Color(0xFF2E7D32)) { onNavigate(Screen.SecurityPrivacy.route) }
                ProfileMenuItem(Icons.Filled.People, "Elder Mode & Family Guardians", Color(0xFF6A1B9A)) { onNavigate(Screen.TrustedContacts.route) }
            }

            SectionCard {
                Text("Preferences & System", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.Notifications, "Notification Alerts", Color(0xFF0288D1)) { onNavigate(Screen.NotificationSettings.route) }
                ProfileMenuItem(Icons.Filled.BatteryChargingFull, "Battery Whitelist & Autostart", Color(0xFFE65100)) {
                    BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
                }
            }

            SectionCard {
                Text("Support & About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextSecondary)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.Help, "Help & Government Helplines", Color(0xFF00838F)) { onNavigate(Screen.HelpSupport.route) }
                ProfileMenuItem(Icons.Filled.Info, "About Rakshak Setu (SIH 2026)", Color(0xFF558B2F)) { onNavigate(Screen.AboutRakshakSetu.route) }
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
    val context = LocalContext.current
    val store = remember { UserProfileStore(context) }

    var name by remember { mutableStateOf(store.fullName) }
    var email by remember { mutableStateOf(store.email) }
    var phone by remember { mutableStateOf(store.phone) }
    var altPhone by remember { mutableStateOf(store.alternatePhone) }
    var state by remember { mutableStateOf(store.state) }
    var city by remember { mutableStateOf(store.city) }
    var address by remember { mutableStateOf(store.address) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Complainant Details", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("These details pre-fill official cybercrime complaints automatically on NCRP 1930 & Chakshu.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            SectionCard {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Mobile Number *") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = altPhone,
                    onValueChange = { altPhone = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Alternate Mobile Number") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address *") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = state,
                    onValueChange = { state = it },
                    label = { Text("State / UT") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Map, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("City / District") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.LocationCity, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp)) }
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Postal Address") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp)) }
                )
            }
            PrimaryButton(
                text = "Save Complainant Profile",
                onClick = {
                    store.fullName = name
                    store.phone = phone
                    store.alternatePhone = altPhone
                    store.email = email
                    store.state = state
                    store.city = city
                    store.address = address
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Filled.Save
            )
        }
    }
}

// ── TRUSTED CONTACTS (ELDER MODE) ─────────────────────────────
@Composable
fun TrustedContactsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { ElderModeStore(context) }
    var isElderEnabled by remember { mutableStateOf(store.isEnabled) }
    var autoSendSms by remember { mutableStateOf(store.autoSendSmsEnabled) }
    var guardians by remember { mutableStateOf(store.getGuardians()) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Elder Mode & Family Guardians", onBackClick = onBack) },
        containerColor = BackgroundLight,
        floatingActionButton = {
            if (guardians.size < ElderModeStore.MAX_GUARDIANS) {
                FloatingActionButton(
                    containerColor = RakshakSetuBlue,
                    shape = RoundedCornerShape(14.dp),
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = "Add Guardian", tint = SurfaceWhite)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard {
                ToggleRow(
                    title = "Enable Elder Mode",
                    subtitle = "Automated emergency alerts to family members on detected scam calls",
                    checked = isElderEnabled,
                    onToggle = {
                        isElderEnabled = it
                        store.isEnabled = it
                    }
                )
                if (isElderEnabled) {
                    Divider(color = BorderColor, modifier = Modifier.padding(vertical = 6.dp))
                    ToggleRow(
                        title = "Auto-send Emergency SMS",
                        subtitle = "Send SMS with call details when confidence ≥ 85%",
                        checked = autoSendSms,
                        onToggle = {
                            autoSendSms = it
                            store.autoSendSmsEnabled = it
                        }
                    )
                }
            }

            Text("Guardians (${guardians.size}/${ElderModeStore.MAX_GUARDIANS})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            if (guardians.isEmpty()) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite)) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No family guardians added yet. Tap (+) to add trusted family contacts.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                guardians.forEach { guardian ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(RakshakSetuBlue.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(guardian.name.firstOrNull()?.uppercase() ?: "G", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = RakshakSetuBlue)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(guardian.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Emergency Guardian", style = MaterialTheme.typography.labelSmall, color = SafeGreen)
                                Text(guardian.number, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                            Row {
                                IconButton(onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${guardian.number}"))
                                    context.startActivity(intent)
                                }) { Icon(Icons.Filled.Call, contentDescription = "Call", tint = SafeGreen) }
                                IconButton(onClick = {
                                    store.removeGuardian(guardian.number)
                                    guardians = store.getGuardians()
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = BlockedRed.copy(alpha = 0.7f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var gName by remember { mutableStateOf("") }
        var gNumber by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Family Guardian") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = gName,
                        onValueChange = { gName = it },
                        label = { Text("Guardian Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gNumber,
                        onValueChange = { gNumber = it.filter { c -> c.isDigit() || c == '+' } },
                        label = { Text("Mobile Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (gName.isNotBlank() && gNumber.length >= 10) {
                            store.addGuardian(ElderModeStore.Guardian(gName.trim(), gNumber.trim()))
                            guardians = store.getGuardians()
                            showAddDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// ── SECURITY & PRIVACY ────────────────────────────────────────
@Composable
fun SecurityPrivacyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val consentStore = remember { ConsentStore(context) }
    var isShieldActive by remember { mutableStateOf(consentStore.isShieldActive) }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Security & Privacy", onBackClick = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionCard {
                Text("Real-Time Defense", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ToggleRow("Call Shield Monitoring", "Actively analyze post-call audio locally", isShieldActive) { active ->
                    isShieldActive = active
                    consentStore.isShieldActive = active
                    if (active) {
                        com.rakshaksetu.app.service.RakshakShieldService.start(context)
                    } else {
                        com.rakshaksetu.app.service.RakshakShieldService.stop(context)
                    }
                }
            }

            SectionCard {
                Text("Device Optimizations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.BatteryChargingFull, "Request Battery Whitelist", RakshakSetuBlue) {
                    BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
                }
                Divider(color = BorderColor)
                ProfileMenuItem(Icons.Filled.Settings, "OEM Autostart Settings", Color(0xFF607D8B)) {
                    BatteryOptimizationHelper.openManufacturerBatterySettings(context)
                }
            }

            SectionCard {
                Text("DPDP Act Compliance & Data Purge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                ProfileMenuItem(Icons.Filled.DeleteForever, "Purge All Evidence & Logs", BlockedRed) {
                    consentStore.purgeEvidence(context)
                    scope.launch {
                        snackbarHostState.showSnackbar("All local evidence & audio logs completely purged.")
                    }
                }
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
            colors = SwitchDefaults.colors(checkedThumbColor = SurfaceWhite, checkedTrackColor = RakshakSetuBlue)
        )
    }
}

// ── NOTIFICATION SETTINGS ─────────────────────────────────────
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val settings = remember {
        mutableStateListOf(
            Triple("Red Alert High-Risk Notifications", "Critical threat detected", true),
            Triple("Yellow Alert Warning Notifications", "Suspicious activity detected", true),
            Triple("Call Analysis Completed Notice", "When a call scan finishes", true),
            Triple("Elder Mode Emergency SMS Alerts", "Auto-alert family guardians", true)
        )
    }

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Notification Preferences", onBackClick = onBack) },
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
    val context = LocalContext.current
    val faqs = listOf(
        "How does AI voice clone detection work?" to "Rakshak Setu uses the AASIST graph neural network model to evaluate spectral phase anomalies in synthetic audio and detect neural vocoder artifacts in real-time.",
        "Is my call audio uploaded to the cloud?" to "No. In accordance with the DPDP Act 2023, all audio transcription, intent matching, and deepfake verification happen 100% locally on your phone.",
        "What happens during a Red Alert?" to "When a high-confidence voice clone or digital arrest scam is detected, Rakshak Setu fires an urgent system alert, notifies family guardians if Elder Mode is enabled, and generates a legal complaint dossier for 1930.",
        "How do I file a Cybercrime report?" to "Use the Reports tab or tap 'NCRP Portal' to open the guided automated reporting tool pre-filled with the scammer's phone number and incident transcript.",
        "What is Golden Hour in cyber fraud?" to "Calling the 1930 helpline within 1 to 2 hours of a fraudulent transaction allows banks and law enforcement to freeze the stolen funds before the fraudster withdraws them."
    )

    Scaffold(
        topBar = { RakshakSetuTopBar(title = "Help & Helplines", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Quick contact
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceWhite), elevation = CardDefaults.cardElevation(2.dp)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                        context.startActivity(intent)
                    }) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(RakshakSetuBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Call, contentDescription = "1930", tint = RakshakSetuBlue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Call 1930", style = MaterialTheme.typography.labelSmall, color = RakshakSetuBlue, fontWeight = FontWeight.SemiBold)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).clickable {
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:report.phishing@cybercrime.gov.in")
                            putExtra(Intent.EXTRA_SUBJECT, "Assistance Request - Rakshak Setu")
                        }
                        context.startActivity(intent)
                    }) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(RakshakSetuBlue.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Email, contentDescription = "Email", tint = RakshakSetuBlue, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Email Cybercell", style = MaterialTheme.typography.labelSmall, color = RakshakSetuBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Text("Frequently Asked Questions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
fun AboutRakshakSetuScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { RakshakSetuTopBar(title = "About Rakshak Setu", onBackClick = onBack) },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(64.dp))
            Text("Rakshak Setu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = RakshakSetuBlue)
            Text("AI Voice Cloning & Telecom Fraud Defense System", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)

            SectionCard {
                listOf(
                    "Challenge" to "Smart India Hackathon 2026",
                    "Problem ID" to "SIH26104",
                    "Core AI" to "AASIST Deepfake Audio Detector",
                    "Speech Engine" to "Vosk Kaldi Offline ASR",
                    "Semantic Engine" to "MiniLM Intent Embeddings",
                    "Architecture" to "100% On-Device AI (DPDP Compliant)"
                ).forEach { (k, v) ->
                    AnalysisRow(k, v)
                }
            }

            Text(
                "Rakshak Setu empowers citizens against sophisticated AI impersonation attacks by analyzing voice patterns, acoustic synthetic signatures, and pressure tactics in real-time, providing immediate intervention and legal complaint generation for the 1930 National Cyber Crime Reporting Portal.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ── SETTINGS ──────────────────────────────────────────────────
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    PersonalInfoScreen(onBack = onBack)
}

// ── SAVED ITEMS ───────────────────────────────────────────────
@Composable
fun SavedItemsScreen(onBack: () -> Unit) {
    ReportsScreen(onNavigate = {}, onBack = onBack)
}
