package com.rakshaksetu.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.core.content.ContextCompat
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.theme.*
import com.rakshaksetu.app.service.BatteryOptimizationHelper
import kotlinx.coroutines.delay

// ── SPLASH SCREEN ──────────────────────────────────────────────
@Composable
fun SplashScreen(onFinished: (Boolean) -> Unit) {
    val context = LocalContext.current
    val alpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000),
        label = "alpha"
    )
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        delay(1600)
        val prefs = context.getSharedPreferences("rakshak_prefs", Context.MODE_PRIVATE)
        val hasCompletedOnboarding = prefs.getBoolean("onboarding_complete", false)
        val hasPhonePerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        onFinished(hasCompletedOnboarding || hasPhonePerm)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF42A5F5)))
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .alpha(alpha)
                .scale(scale)
        ) {
            Icon(
                Icons.Filled.Shield,
                contentDescription = "Rakshak Setu Logo",
                tint = SurfaceWhite,
                modifier = Modifier.size(80.dp)
            )
            Text(
                "Rakshak Setu",
                style = MaterialTheme.typography.displayLarge,
                color = SurfaceWhite,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "AI Voice Clone & Scam Defense",
                style = MaterialTheme.typography.bodyLarge,
                color = SurfaceWhite.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(color = SurfaceWhite, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

// ── BEFORE LOGIN SCREEN ────────────────────────────────────────
@Composable
fun BeforeLoginScreen(onGetStarted: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(48.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(RakshakSetuBlueLight, RakshakSetuBlue.copy(alpha = 0.1f)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(64.dp))
                }

                Spacer(Modifier.height(32.dp))
                Text("Stay Safe. Stay Ahead.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = TextPrimary, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Real-time on-device AI protection against AI voice cloning, deepfake audio impersonation, and phone fraud.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrimaryButton(
                    text = "Get Started",
                    onClick = onGetStarted,
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Filled.ArrowForward
                )
                Text(
                    "Smart India Hackathon 2026 (SIH26104)",
                    style = MaterialTheme.typography.bodySmall,
                    color = RakshakSetuBlue,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

// ── LOGIN SCREEN ───────────────────────────────────────────────
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var phoneInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(40.dp))
        Icon(Icons.Filled.Shield, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(56.dp))
        Text("Get Started with Rakshak Setu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Set up your device protection in seconds", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = phoneInput,
            onValueChange = { phoneInput = it.filter { c -> c.isDigit() || c == '+' } },
            label = { Text("Mobile Number") },
            placeholder = { Text("+91 98765 43210") },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null, tint = RakshakSetuBlue) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        PrimaryButton(
            text = "Continue",
            onClick = {
                val prefs = context.getSharedPreferences("rakshak_prefs", Context.MODE_PRIVATE)
                prefs.edit().putString("user_phone", phoneInput).apply()
                onLoginSuccess()
            },
            modifier = Modifier.fillMaxWidth()
        )

        Text("— OR —", style = MaterialTheme.typography.labelMedium, color = TextSecondary)

        OutlinedButton(
            onClick = onLoginSuccess,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, BorderColor)
        ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Continue as Local Guest", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ── TERMS SCREEN ───────────────────────────────────────────────
@Composable
fun TermsScreen(onAgree: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
    ) {
        RakshakSetuTopBar(title = "Privacy & Consent")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("DPDP Act 2023 Compliant Consent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Rakshak Setu processes call metadata and audio features 100% on your device to protect you against voice cloning impersonation and financial fraud.\n\n" +
                "• 100% On-Device AI: Audio processing, speech recognition, and deepfake detection run locally on your phone.\n" +
                "• Zero Audio Uploads: Raw phone call audio is never uploaded to any remote server or cloud service.\n" +
                "• Explicit User Control: You can pause or stop the shield anytime with a single tap from the dashboard.\n" +
                "• Complete Data Purge: You can delete all local detection logs and cached evidence at any time.\n" +
                "• Secure Government Filing: Incident reports for National Cyber Crime Portal (NCRP 1930 / Chakshu) are generated locally under your control.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                lineHeight = 22.sp
            )
        }
        Box(modifier = Modifier.padding(24.dp)) {
            PrimaryButton(text = "I Agree & Continue", onClick = onAgree, modifier = Modifier.fillMaxWidth())
        }
    }
}

// ── PERMISSION EDUCATION ───────────────────────────────────────
@Composable
fun PermissionEducationScreen(onContinue: () -> Unit) {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        val prefs = context.getSharedPreferences("rakshak_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("onboarding_complete", true).apply()
        try {
            BatteryOptimizationHelper.requestBatteryOptimizationExemption(context)
        } catch (ignored: Exception) {}
        onContinue()
    }

    val permissions = listOf(
        Triple(Icons.Filled.Phone, "Telephony / Call State", "Detects when phone calls end to run on-device scam analysis"),
        Triple(Icons.Filled.Notifications, "Notifications", "Sends immediate high-priority alerts if a scam or clone is detected"),
        Triple(Icons.Filled.Sms, "Emergency SMS", "Allows Elder Mode to alert trusted family guardians in emergencies"),
        Triple(Icons.Filled.Mic, "Audio Analysis", "Enables on-device speech recognition & AASIST deepfake detection"),
        Triple(Icons.Filled.BatteryChargingFull, "Battery Optimization", "Keeps background shield active without being killed by the OS")
    )

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        RakshakSetuTopBar(title = "Required Permissions")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("System Permissions", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Rakshak Setu requires these Android permissions to safeguard you from fraud calls in real-time.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            permissions.forEach { (icon, title, reason) ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(RakshakSetuBlue.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(22.dp))
                        }
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(reason, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
        Box(modifier = Modifier.padding(24.dp)) {
            PrimaryButton(
                text = "Grant Permissions & Continue",
                onClick = {
                    val perms = mutableListOf(
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.CALL_PHONE,
                        Manifest.permission.RECORD_AUDIO
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms.add(Manifest.permission.POST_NOTIFICATIONS)
                        perms.add(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    permissionLauncher.launch(perms.toTypedArray())
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── SECURITY TOUR ──────────────────────────────────────────────
@Composable
fun SecurityTourScreen(onContinue: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }

    data class TourPage(
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val color: androidx.compose.ui.graphics.Color,
        val title: String,
        val body: String
    )

    val pages = listOf(
        TourPage(
            Icons.Filled.Dangerous,
            BlockedRed,
            "Red Alert — Voice Clone & High Threat",
            "A confirmed high-risk AI voice clone or financial extortion script was detected. Do NOT send money, share OTPs, or transfer bank funds."
        ),
        TourPage(
            Icons.Filled.Warning,
            SuspiciousAmber,
            "Yellow Alert — Suspicious Call Pattern",
            "Unusual pressure tactics, urgent bank KYC threats, or unverified caller numbers detected. Proceed with caution."
        )
    )

    val (icon, color, title, body) = pages[page]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundLight)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(24.dp))
        StepProgress(2, page)
        Spacer(Modifier.height(48.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Box(
                modifier = Modifier.size(100.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(56.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = TextPrimary, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = TextSecondary, textAlign = TextAlign.Center, lineHeight = 24.sp)
        }

        if (page == 0) {
            PrimaryButton("Next", onClick = { page = 1 }, modifier = Modifier.fillMaxWidth())
        } else {
            PrimaryButton("Start Protecting Me", onClick = onContinue, modifier = Modifier.fillMaxWidth(), icon = Icons.Filled.Shield)
        }
    }
}

// ── BANK SETUP SCREEN ──────────────────────────────────────────
@Composable
fun BankSetupScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    var bankName by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        RakshakSetuTopBar(title = "Emergency & Reporting Profile")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Optional Profile Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Add your details for faster 1-tap Cybercrime 1930 / NCRP portal filing. All stored locally on your phone.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

            SectionCard {
                Surface(color = RakshakSetuBlue.copy(alpha = 0.08f), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(18.dp))
                        Text("This info is saved locally and used to pre-fill incident reports.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Primary Bank Name") },
                    placeholder = { Text("e.g. State Bank of India") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it },
                    label = { Text("UPI ID (Optional)") },
                    placeholder = { Text("e.g. name@bank") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SecondaryButton("Skip", onClick = onContinue, modifier = Modifier.weight(1f))
            PrimaryButton("Save & Finish", onClick = {
                val prefs = context.getSharedPreferences("rakshak_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("onboarding_complete", true).apply()
                onContinue()
            }, modifier = Modifier.weight(2f))
        }
    }
}
