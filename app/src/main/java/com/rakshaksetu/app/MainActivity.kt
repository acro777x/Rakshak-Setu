package com.rakshaksetu.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rakshaksetu.app.action.BankEmailAction
import com.rakshaksetu.app.action.GovtPortalAction
import com.rakshaksetu.app.action.HelplineAction
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.notification.ScamAlertManager
import com.rakshaksetu.app.service.AnalysisService
import com.rakshaksetu.app.service.BatteryOptimizationHelper
import com.rakshaksetu.app.telephony.RakshakCallStateListener
import com.rakshaksetu.app.ui.EvidenceActivity
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure Telephony Listener is registered
        try {
            RakshakCallStateListener.register(applicationContext)
        } catch (e: Exception) {
            // Ignore error
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF2E7D32),
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

    // Permission states
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

    var isBatteryOptimizedIgnored by remember {
        mutableStateOf(BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context))
    }

    var hasFullScreenPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = context.getSystemService(android.app.NotificationManager::class.java)
                nm?.canUseFullScreenIntent() == true
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPhonePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotificationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        scope.launch {
            if (results.all { it.value }) {
                snackbarHostState.showSnackbar("All permissions granted! Shield Active.")
            } else {
                snackbarHostState.showSnackbar("Some permissions were denied.")
            }
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
                            tint = if (isShieldActive) Color(0xFF4CAF50) else Color(0xFFFF9800),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rakshak Setu", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
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
            // 1. Shield Status Card (DPDP Active / Paused)
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
                            color = Color(0xFFE0E0E0)
                        )
                    }
                    Switch(
                        checked = isShieldActive,
                        onCheckedChange = { active ->
                            isShieldActive = active
                            consentStore.isShieldActive = active
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (active) "Shield activated" else "Shield paused (DPDP Opt-out)"
                                )
                            }
                        }
                    )
                }
            }

            // 2. Permission Checklist
            Text("System Permissions & Whitelist", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF242424)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Phone State
                    PermissionItem(
                        title = "Telephony / Call State",
                        subtitle = "Required to detect call-end events",
                        isGranted = hasPhonePermission,
                        onGrant = {
                            val perms = mutableListOf(
                                Manifest.permission.READ_PHONE_STATE,
                                Manifest.permission.READ_CALL_LOG
                            )
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                            } else {
                                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }
                    )

                    HorizontalDivider(color = Color(0xFF333333))

                    // Notifications
                    PermissionItem(
                        title = "Push Notifications",
                        subtitle = "Required for instant post-call scam alerts",
                        isGranted = hasNotificationPermission,
                        onGrant = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }
                    )

                    HorizontalDivider(color = Color(0xFF333333))

                    // Battery Optimization
                    PermissionItem(
                        title = "Background Battery Whitelist",
                        subtitle = "Prevents OS from killing analysis service",
                        isGranted = isBatteryOptimizedIgnored,
                        onGrant = {
                            try {
                                BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
                                isBatteryOptimizedIgnored = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
                            } catch (e: Exception) {
                                scope.launch { snackbarHostState.showSnackbar("Unable to open battery settings") }
                            }
                        }
                    )

                    if (Build.VERSION.SDK_INT >= 34) {
                        HorizontalDivider(color = Color(0xFF333333))
                        PermissionItem(
                            title = "Full-Screen Alert Intent",
                            subtitle = "Fires full-screen alert on locked phones",
                            isGranted = hasFullScreenPermission,
                            onGrant = {
                                try {
                                    context.startActivity(Intent(
                                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                        Uri.parse("package:${context.packageName}")
                                    ))
                                } catch (e: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Settings not accessible") }
                                }
                            }
                        )
                    }
                }
            }

            // 3. Testing & Simulation Buttons (BuildConfig.DEBUG & Demo)
            Text("Simulate AI Pipeline (Test Actions)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val fake = FakePipelineEmitter.scamResult()
                            DetectionStore.saveLastResult(context, fake)
                            lastDetection = fake

                            // Trigger alert notification immediately for zero-latency test feedback
                            ScamAlertManager(context).showScamAlert(fake)

                            val serviceIntent = Intent(context, AnalysisService::class.java).apply {
                                putExtra(AnalysisService.EXTRA_CALL_ID, fake.callId)
                                putExtra(AnalysisService.EXTRA_PHONE_NUMBER, fake.phoneNumber)
                                putExtra(AnalysisService.EXTRA_IS_SIMULATION, true)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            scope.launch { snackbarHostState.showSnackbar("AnalysisService triggered with SCAM call!") }
                        } catch (e: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("Error triggering test: ${e.message}") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Scam Alert", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        try {
                            val fake = FakePipelineEmitter.benignResult()
                            DetectionStore.saveLastResult(context, fake)
                            lastDetection = fake

                            val serviceIntent = Intent(context, AnalysisService::class.java).apply {
                                putExtra(AnalysisService.EXTRA_CALL_ID, fake.callId)
                                putExtra(AnalysisService.EXTRA_PHONE_NUMBER, fake.phoneNumber)
                                putExtra(AnalysisService.EXTRA_IS_SIMULATION, true)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(serviceIntent)
                            } else {
                                context.startService(serviceIntent)
                            }
                            scope.launch { snackbarHostState.showSnackbar("AnalysisService triggered with BENIGN call.") }
                        } catch (e: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("Error triggering test: ${e.message}") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test Benign Call", fontSize = 12.sp)
                }
            }

            // 4. Last Detection Result Card
            Text("Latest Detection Result", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF212121)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val result = lastDetection
                    if (result != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (result.isScam) "⚠️ ${result.scamType?.replace("_", " ")?.uppercase() ?: "SCAM"}" else "✅ SAFE CALL",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (result.isScam) Color(0xFFFF5252) else Color(0xFF81C784)
                            )
                            Badge(containerColor = if (result.isScam) Color(0xFFD32F2F) else Color(0xFF388E3C)) {
                                Text("${(result.confidence * 100).toInt()}% Conf", color = Color.White, modifier = Modifier.padding(4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Phone: ${result.phoneNumber} • Duration: ${result.durationSec}s", color = Color(0xFFB0BEC5), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(context, EvidenceActivity::class.java).apply {
                                        putExtra(ScamAlertManager.EXTRA_CALL_ID, result.callId)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Cannot open Evidence: ${e.message}") }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Evidence Dossier")
                        }
                    } else {
                        Text("No detections yet. Simulated calls or actual phone calls will appear here.", color = Color(0xFF757575), fontSize = 13.sp)
                    }
                }
            }

            // 5. Quick Actions Row
            Text("Quick Action Hub", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            HelplineAction.dial1930(context)
                        } catch (e: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("Failed to dial 1930") }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Call 1930", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val dummy = lastDetection ?: FakePipelineEmitter.scamResult()
                            val bank = BankEmailAction.getBanks().first()
                            val intent = BankEmailAction.buildEmailIntent(dummy, bank)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("No email app installed") }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Bank Mail", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            GovtPortalAction.openChakshu(context)
                        } catch (e: Exception) {
                            scope.launch { snackbarHostState.showSnackbar("Cannot open browser") }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Chakshu", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Color(0xFF9E9E9E))
        }
        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )
        } else {
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Grant", fontSize = 12.sp)
            }
        }
    }
}
