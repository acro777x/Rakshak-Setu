package com.rakshaksetu.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rakshaksetu.app.action.BankEmailAction
import com.rakshaksetu.app.action.GovtPortalAction
import com.rakshaksetu.app.action.HelplineAction
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.elder.ElderModeStore
import com.rakshaksetu.app.elder.RakshakAppTheme
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.pipeline.ModelDownloadManager
import com.rakshaksetu.app.report.UserProfileStore
import com.rakshaksetu.app.service.AnalysisService
import com.rakshaksetu.app.service.BatteryOptimizationHelper
import com.rakshaksetu.app.telephony.RakshakCallStateListener
import com.rakshaksetu.app.ui.EvidenceActivity
import com.rakshaksetu.app.ui.GovtReportWebViewActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            RakshakCallStateListener.register(applicationContext)
        } catch (ignored: Exception) {
        }

        val elderMode = ElderModeStore(applicationContext).isEnabled

        setContent {
            RakshakAppTheme(elderModeEnabled = elderMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainDashboardScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val consentStore = remember { ConsentStore(context) }
    var isShieldActive by remember { mutableStateOf(consentStore.isShieldActive) }
    var lastDetection by remember { mutableStateOf<DetectionResult?>(DetectionStore.getLastResult(context)) }

    var hasPhonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var isBatteryOptimizedIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }
    var hasFullScreenPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 34) {
                context.getSystemService(android.app.NotificationManager::class.java)?.canUseFullScreenIntent() == true
            } else true
        )
    }

    fun refreshPhone() {
        hasPhonePermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        hasSmsPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission =
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refreshPhone()
        scope.launch {
            snackbarHostState.showSnackbar(
                if (results.all { it.value }) "Permissions granted! Shield Active."
                else "Some permissions were denied."
            )
        }
    }

    LaunchedEffect(isShieldActive) {
        if (isShieldActive) {
            com.rakshaksetu.app.service.RakshakShieldService.start(context)
        } else {
            com.rakshaksetu.app.service.RakshakShieldService.stop(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Shield",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rakshak Setu", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
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
            ShieldStatusCard(isShieldActive) { active ->
                isShieldActive = active
                consentStore.isShieldActive = active
                if (active) {
                    com.rakshaksetu.app.service.RakshakShieldService.start(context)
                } else {
                    com.rakshaksetu.app.service.RakshakShieldService.stop(context)
                }
            }

            PermissionChecklistSection(
                hasPhonePermission = hasPhonePermission,
                hasNotificationPermission = hasNotificationPermission,
                hasSmsPermission = hasSmsPermission,
                isBatteryOptimizedIgnored = isBatteryOptimizedIgnored,
                hasFullScreenPermission = hasFullScreenPermission,
                onGrantPhone = {
                    val perms = mutableListOf(
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.SEND_SMS
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                },
                onGrantNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                },
                onGrantSms = {
                    permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
                },
                onGrantBattery = {
                    try {
                        BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
                        isBatteryOptimizedIgnored =
                            BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                    } catch (e: Exception) {
                        scope.launch { snackbarHostState.showSnackbar("Unable to open battery settings") }
                    }
                },
                onGrantFullScreen = {
                    try {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    } catch (e: Exception) {
                        scope.launch { snackbarHostState.showSnackbar("Settings not accessible") }
                    }
                }
            )

            AiModelsSection()

            ElderModeSection(hasSmsPermission = hasSmsPermission, onRequestSms = {
                permissionLauncher.launch(arrayOf(Manifest.permission.SEND_SMS))
            })

            ComplaintProfileSection()

            if (com.rakshaksetu.app.BuildConfig.DEBUG) {
                SimulationSection(onResultSaved = { lastDetection = it })
            }

            LastDetectionSection(lastDetection) { refreshed -> lastDetection = refreshed }

            QuickActionsSection(lastDetection)

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ShieldStatusCard(isShieldActive: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isShieldActive) Color(0xFF1B381E) else Color(0xFF382A1B)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isShieldActive) "SHIELD ACTIVE" else "SHIELD PAUSED",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (isShieldActive) Color(0xFF81C784) else Color(0xFFFFB74D)
                )
                Text(
                    text = if (isShieldActive)
                        "On-device AI actively monitoring post-call scam audio."
                    else
                        "Monitoring paused. No call audio is processed.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = isShieldActive, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun PermissionChecklistSection(
    hasPhonePermission: Boolean,
    hasNotificationPermission: Boolean,
    hasSmsPermission: Boolean,
    isBatteryOptimizedIgnored: Boolean,
    hasFullScreenPermission: Boolean,
    onGrantPhone: () -> Unit,
    onGrantNotifications: () -> Unit,
    onGrantSms: () -> Unit,
    onGrantBattery: () -> Unit,
    onGrantFullScreen: () -> Unit
) {
    Text("System Permissions & Whitelist", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PermissionItem("Telephony / Call State", "Required to detect call-end events", hasPhonePermission, onGrantPhone)
            HorizontalDivider(color = Color(0xFF333333))
            PermissionItem("Push Notifications", "Required for instant post-call scam alerts", hasNotificationPermission, onGrantNotifications)
            HorizontalDivider(color = Color(0xFF333333))
            PermissionItem("Emergency Family SMS", "Elder Mode guardians get SMS alerts", hasSmsPermission, onGrantSms)
            HorizontalDivider(color = Color(0xFF333333))
            PermissionItem("Background Battery Whitelist", "Prevents OS from killing analysis service", isBatteryOptimizedIgnored, onGrantBattery)
            if (Build.VERSION.SDK_INT >= 34) {
                HorizontalDivider(color = Color(0xFF333333))
                PermissionItem("Full-Screen Alert Intent", "Fires full-screen alert on locked phones", hasFullScreenPermission, onGrantFullScreen)
            }
            HorizontalDivider(color = Color(0xFF333333))
            OemAutostartItem()
        }
    }
}

@Composable
private fun OemAutostartItem() {
    val context = LocalContext.current
    var showInstructions by remember { mutableStateOf(false) }

    Column {
        PermissionItem(
            title = "${BatteryOptimizationHelper.getDetectedBrandName()} Autostart",
            subtitle = "Open OEM battery manager and allow background run",
            isGranted = false,
            grantLabel = "Open",
            onGrant = { showInstructions = true; BatteryOptimizationHelper.openManufacturerBatterySettings(context) }
        )
        if (showInstructions) {
            Column(Modifier.padding(top = 6.dp)) {
                BatteryOptimizationHelper.getOemInstructions().forEach {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AiModelsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var selectedLang by remember { mutableStateOf(ModelDownloadManager.selectedLanguage(context)) }
    var asrReady by remember { mutableStateOf(ModelDownloadManager.isAsrModelReady(context)) }
    var embedReady by remember { mutableStateOf(ModelDownloadManager.isEmbeddingModelReady(context)) }
    var busy by remember { mutableStateOf(false) }
    var progressText by remember { mutableStateOf("") }
    var langMenuOpen by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("On-Device AI Models", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                "Offline scam detection runs fully on your phone. Download once over Wi-Fi (~40 MB); the app stays under 20 MB.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Box {
                OutlinedButton(onClick = { langMenuOpen = true }, enabled = !busy) {
                    Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Language: ${ModelDownloadManager.specFor(selectedLang).displayName}")
                }
                DropdownMenu(expanded = langMenuOpen, onDismissRequest = { langMenuOpen = false }) {
                    ModelDownloadManager.availableLanguages().forEach { spec ->
                        DropdownMenuItem(
                            text = { Text(spec.displayName) },
                            onClick = {
                                langMenuOpen = false
                                selectedLang = spec.langKey
                                ModelDownloadManager.setSelectedLanguage(context, spec.langKey)
                                asrReady = ModelDownloadManager.isAsrModelReady(context)
                            }
                        )
                    }
                }
            }

            StatusDot("Speech recognition (Vosk)", asrReady)
            StatusDot("Semantic phrase encoder (MiniLM)", embedReady)
            var deepfakeReady by remember { mutableStateOf(ModelDownloadManager.isDeepfakeModelReady(context)) }
            StatusDot("Voice clone detector (AASIST)", deepfakeReady)

            if (progressText.isNotBlank()) {
                LinearProgressIndicator(progress = { progressFloatFrom(progressText) }, modifier = Modifier.fillMaxWidth())
                Text(progressText, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            ModelDownloadManager.downloadAsrModel(context, selectedLang).collectLatest { state ->
                                when (state) {
                                    is ModelDownloadManager.DownloadState.Downloading ->
                                        progressText = "Downloading ${(state.progress * 100).toInt()}%"
                                    is ModelDownloadManager.DownloadState.Extracting ->
                                        progressText = "Extracting model..."
                                    is ModelDownloadManager.DownloadState.Success -> {
                                        asrReady = true
                                        progressText = ""
                                        snackbar.showSnackbar("ASR model ready — offline detection upgraded!")
                                    }
                                    is ModelDownloadManager.DownloadState.Error -> {
                                        progressText = ""
                                        snackbar.showSnackbar("Model error: ${state.message}")
                                    }
                                    else -> {}
                                }
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && !asrReady
                ) {
                    Text(if (asrReady) "ASR Ready" else "Download Speech Model")
                }

                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            ModelDownloadManager.downloadEmbeddingModel(context).collectLatest { state ->
                                when (state) {
                                    is ModelDownloadManager.DownloadState.Success -> embedReady = true
                                    is ModelDownloadManager.DownloadState.Error ->
                                        snackbar.showSnackbar("Encoder error: ${state.message}")
                                    else -> {}
                                }
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && !embedReady
                ) {
                    Text(if (embedReady) "Encoder Ready" else "Get Encoder")
                }

                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            ModelDownloadManager.downloadDeepfakeModel(context).collectLatest { state ->
                                when (state) {
                                    is ModelDownloadManager.DownloadState.Downloading ->
                                        progressText = "AASIST: ${state.fileName} ${(state.progress * 100).toInt()}%"
                                    is ModelDownloadManager.DownloadState.Success -> {
                                        deepfakeReady = true
                                        progressText = "AASIST voice clone model ready!"
                                    }
                                    is ModelDownloadManager.DownloadState.Error ->
                                        snackbar.showSnackbar("AASIST error: ${state.message}")
                                    else -> {}
                                }
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && !deepfakeReady
                ) {
                    Text(if (deepfakeReady) "🛡️ AASIST Ready" else "Get AASIST Clone Detector")
                }
            }
        }
    }
}

private fun progressFloatFrom(text: String): Float {
    return Regex("(\\d+)%").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.div(100f) ?: 0f
}

@Composable
private fun StatusDot(label: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Default.Circle,
            contentDescription = null,
            tint = if (ready) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(10.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("$label — ${if (ready) "installed" else "not installed"}", fontSize = 13.sp)
    }
}

@Composable
private fun ElderModeSection(hasSmsPermission: Boolean, onRequestSms: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val store = remember { ElderModeStore(context) }

    var enabled by remember { mutableStateOf(store.isEnabled) }
    var autoSend by remember { mutableStateOf(store.autoSendSmsEnabled) }
    var guardians by remember { mutableStateOf(store.getGuardians()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var gatewayUrl by remember { mutableStateOf(store.smsGatewayUrl) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Elder Mode", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "High-contrast UI, larger text, and emergency family SMS on high-confidence scam calls.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        store.isEnabled = on
                    }
                )
            }

            if (enabled) {
                var showGateway by remember { mutableStateOf(store.smsGatewayUrl.isNotBlank()) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoSend, onCheckedChange = { checked ->
                        if (checked && !hasSmsPermission) {
                            onRequestSms()
                        }
                        autoSend = checked && hasSmsPermission
                        store.autoSendSmsEnabled = autoSend
                    })
                    Text("Auto-send SMS at ≥85% confidence (opt-in)", fontSize = 13.sp)
                }

                Text("Guardians (${guardians.size}/${ElderModeStore.MAX_GUARDIANS})", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                guardians.forEach { guardian ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(guardian.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(guardian.number, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            store.removeGuardian(guardian.number)
                            guardians = store.getGuardians()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove ${guardian.name}", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Button(
                    onClick = { showAddDialog = true },
                    enabled = guardians.size < ElderModeStore.MAX_GUARDIANS
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Guardian")
                }

                TextButton(onClick = { showGateway = !showGateway }) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (store.smsGatewayUrl.isBlank()) "Add backup SMS gateway (optional)"
                        else "SMS gateway configured ✓ — tap to change",
                        fontSize = 13.sp
                    )
                }
                if (showGateway) {
                    OutlinedTextField(
                        value = gatewayUrl,
                        onValueChange = {
                            gatewayUrl = it
                            store.smsGatewayUrl = it
                        },
                        label = { Text("HTTP SMS gateway URL") },
                        placeholder = { Text("https://... or https://api?to={to}&msg={body}") },
                        supportingText = { Text("Direct SMS is tried first; gateway is the fallback. Works with any provider API you have.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (!hasSmsPermission) {
                    Text(
                        "SMS permission needed to alert guardians.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        GuardianDialog(
            onDismiss = { showAddDialog = false },
            onSave = { name, number ->
                if (store.addGuardian(ElderModeStore.Guardian(name.trim(), number.trim()))) {
                    guardians = store.getGuardians()
                    showAddDialog = false
                } else {
                    scope.launch { snackbar.showSnackbar("Invalid name/number, duplicate, or limit reached") }
                }
            }
        )
    }
}

@Composable
private fun GuardianDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Family Guardian") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = number,
                    onValueChange = { number = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Mobile Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    supportingText = { Text("Indian mobile or international format") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, number) },
                enabled = name.isNotBlank() && number.filter { it.isDigit() }.length >= 10
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ComplaintProfileSection() {
    val context = LocalContext.current
    val store = remember { UserProfileStore(context) }
    var showDialog by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Complainant Profile", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(
                if (store.isCompleteForFiling()) "Name: ${store.fullName}\nMobile: ${store.phone}\nEmail: ${store.email}"
                else "Add your details so government portals (NCRP / Chakshu) can be pre-filled automatically.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (store.fullName.isBlank()) "Add Details" else "Edit Details")
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(context, GovtReportWebViewActivity::class.java))
                }) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Report Portal")
                }
            }
        }
    }

    if (showDialog) {
        ProfileDialog(
            store = store,
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun ProfileDialog(store: UserProfileStore, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(store.fullName) }
    var phone by remember { mutableStateOf(store.phone) }
    var altPhone by remember { mutableStateOf(store.alternatePhone) }
    var email by remember { mutableStateOf(store.email) }
    var state by remember { mutableStateOf(store.state) }
    var city by remember { mutableStateOf(store.city) }
    var address by remember { mutableStateOf(store.address) }
    var age by remember { mutableStateOf(store.ageDeclared) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complainant Details") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name *") }, singleLine = true)
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Mobile *") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = altPhone,
                    onValueChange = { altPhone = it.filter { c -> c.isDigit() || c == '+' } },
                    label = { Text("Alternate Mobile") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email *") }, singleLine = true)
                OutlinedTextField(
                    value = age,
                    onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Age") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(value = state, onValueChange = { state = it }, label = { Text("State / UT") }, singleLine = true)
                OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City / District") }, singleLine = true)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Full Address") },
                    minLines = 2,
                    maxLines = 4
                )
                Text(
                    "* Required for NCRP filing. All data stays on-device only.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                store.fullName = name
                store.phone = phone
                store.alternatePhone = altPhone
                store.email = email
                store.state = state
                store.city = city
                store.address = address
                store.ageDeclared = age
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun SimulationSection(onResultSaved: (DetectionResult) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    fun triggerScenario(result: DetectionResult, label: String) {
        try {
            DetectionStore.saveLastResult(context, result)
            onResultSaved(result)
            ScamAlertManager(context).showScamAlert(result)
            val serviceIntent = Intent(context, AnalysisService::class.java).apply {
                putExtra(AnalysisService.EXTRA_CALL_ID, result.callId)
                putExtra(AnalysisService.EXTRA_PHONE_NUMBER, result.phoneNumber)
                putExtra(AnalysisService.EXTRA_IS_SIMULATION, true)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            scope.launch { snackbar.showSnackbar("Triggered: $label") }
        } catch (e: Exception) {
            scope.launch { snackbar.showSnackbar("Error: ${e.message}") }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("🧪 Threat Simulation & Test Studio", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "Tap any scenario to simulate a real call and verify AI alerts & evidence:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { triggerScenario(FakePipelineEmitter.voiceCloneResult(), "AI Voice Clone Attack") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF880E4F)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("🎭 Test AI Voice Clone Attack (SIH26104)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { triggerScenario(FakePipelineEmitter.digitalArrestResult(), "CBI Digital Arrest") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    modifier = Modifier.weight(1f)
                ) { Text("🚨 Digital Arrest", fontSize = 11.sp, maxLines = 1) }
                Button(
                    onClick = { triggerScenario(FakePipelineEmitter.screenShareResult(), "Screen Share Scam") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                    modifier = Modifier.weight(1f)
                ) { Text("📱 AnyDesk Scam", fontSize = 11.sp, maxLines = 1) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { triggerScenario(FakePipelineEmitter.kycFraudResult(), "Bank KYC Scam") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315)),
                    modifier = Modifier.weight(1f)
                ) { Text("💳 Bank KYC Scam", fontSize = 11.sp, maxLines = 1) }
                Button(
                    onClick = { triggerScenario(FakePipelineEmitter.loanExtortionResult(), "Loan Extortion") },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAD1457)),
                    modifier = Modifier.weight(1f)
                ) { Text("⚖️ Loan Extortion", fontSize = 11.sp, maxLines = 1) }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = { triggerScenario(FakePipelineEmitter.benignResult(), "Safe Call") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("🟢 Test Normal Safe Call", color = Color(0xFF2E7D32), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LastDetectionSection(lastDetection: DetectionResult?, onChanged: (DetectionResult?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                onChanged(DetectionStore.getLastResult(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Text("Latest Detection Result", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            val result = lastDetection
            if (result != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = if (result.isScam) "⚠️ ${result.scamType?.replace("_", " ")?.uppercase() ?: "SCAM"}" else "✅ SAFE CALL", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (result.isScam) MaterialTheme.colorScheme.error else Color(0xFF81C784))
                    Badge(containerColor = if (result.isScam) Color(0xFFD32F2F) else Color(0xFF388E3C)) { Text("${(result.confidence * 100).toInt()}% Conf", color = Color.White, modifier = Modifier.padding(4.dp)) }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Phone: ${result.phoneNumber} • Duration: ${result.durationSec}s • Transcript: ${result.fullTranscript.length} chars", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Button(onClick = {
                    try {
                        val intent = Intent(context, EvidenceActivity::class.java).apply { putExtra(ScamAlertManager.EXTRA_CALL_ID, result.callId) }
                        context.startActivity(intent)
                    } catch (e: Exception) {}
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Evidence Dossier")
                }
            } else {
                Text("No detections yet. Real phone calls will appear here after analysis completes.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun QuickActionsSection(lastDetection: DetectionResult?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    Text("Quick Action Hub", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { try { HelplineAction.dial1930(context) } catch (e: Exception) { scope.launch { snackbar.showSnackbar("Failed to dial 1930") } } }, modifier = Modifier.weight(1f)) { Text("Call 1930", fontSize = 12.sp) }
        OutlinedButton(onClick = { try { val dummy = lastDetection ?: FakePipelineEmitter.digitalArrestResult(); val bank = BankEmailAction.getBanks().first(); context.startActivity(BankEmailAction.buildEmailIntent(dummy, bank)) } catch (e: Exception) { scope.launch { snackbar.showSnackbar("No email app installed") } } }, modifier = Modifier.weight(1f)) { Text("Bank Mail", fontSize = 12.sp) }
        OutlinedButton(onClick = { try { GovtPortalAction.openChakshu(context) } catch (e: Exception) { scope.launch { snackbar.showSnackbar("Cannot open browser") } } }, modifier = Modifier.weight(1f)) { Text("Chakshu", fontSize = 12.sp) }
    }
}

@Composable
fun PermissionItem(title: String, subtitle: String, isGranted: Boolean, onGrant: () -> Unit, grantLabel: String = "Grant") {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isGranted) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Granted", tint = Color(0xFF4CAF50), modifier = Modifier.size(24.dp))
        } else {
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(6.dp)) {
                Text(grantLabel, fontSize = 12.sp)
            }
        }
    }
}
