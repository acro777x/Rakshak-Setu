package com.rakshaksetu.app.ui.screens

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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.rakshaksetu.app.ui.components.*
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.delay

// ── SPLASH SCREEN ──────────────────────────────────────────────
@Composable
fun SplashScreen(onFinished: () -> Unit) {
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
        delay(2200)
        onFinished()
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
                "Your Digital Safety Companion",
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
                    "Your trusted digital safety companion.\nScan calls, links, files and more — all in one place.",
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
                    "Already protected? Sign in",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RakshakSetuBlue,
                    modifier = Modifier
                        .clickable(onClick = onGetStarted)
                        .align(Alignment.CenterHorizontally),
                    textDecoration = TextDecoration.Underline
                )
            }
        }
    }
}

// ── LOGIN SCREEN ───────────────────────────────────────────────
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
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
        Text("Sign in to Rakshak Setu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Choose how you want to sign in", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

        Spacer(Modifier.height(16.dp))

        // Google
        OutlinedButton(
            onClick = onLoginSuccess,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, BorderColor)
        ) {
            Icon(Icons.Filled.Email, contentDescription = null, tint = Color(0xFFEA4335), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Continue with Google", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        }

        // Facebook
        Button(
            onClick = onLoginSuccess,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1877F2))
        ) {
            Icon(Icons.Filled.People, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Continue with Facebook", color = SurfaceWhite, style = MaterialTheme.typography.titleMedium)
        }

        // Email
        OutlinedButton(
            onClick = onLoginSuccess,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.AlternateEmail, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Continue with Email", style = MaterialTheme.typography.titleMedium, color = RakshakSetuBlue)
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
        RakshakSetuTopBar(title = "Terms & Conditions")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Privacy & Terms", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Rakshak Setu is a frontend demo application. All security results are simulated locally. No real personal data is transmitted to any server.\n\n" +
                "• We do not record your calls.\n" +
                "• We do not store your ID documents.\n" +
                "• All scans run locally on your device in this prototype.\n" +
                "• No actual cybercrime submissions are made.\n\n" +
                "By agreeing, you acknowledge this is a frontend prototype and all data is mock/demo data for demonstration purposes only.\n\n" +
                "Rakshak Setu uses probabilistic language for risk assessment. Results should not be relied upon as definitive security guarantees.",
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
    val permissions = listOf(
        Triple(Icons.Filled.Phone, "Call Recording / Audio", "Helps analyze suspicious calls for voice-clone patterns"),
        Triple(Icons.Filled.Sms, "SMS / Notifications", "Detects phishing links in messages"),
        Triple(Icons.Filled.Contacts, "Contacts", "Identifies unknown callers against your contact list"),
        Triple(Icons.Filled.History, "Call Logs", "Reviews call patterns for suspicious activity"),
        Triple(Icons.Filled.Photo, "Photos / Media", "Scans images for hidden QR codes or suspicious content"),
        Triple(Icons.Filled.CameraAlt, "Camera", "Powers the QR code scanner")
    )

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        RakshakSetuTopBar(title = "Permissions")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("What Rakshak Setu needs", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("These permissions help protect you. All data stays on your device in this demo.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

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
            PrimaryButton(text = "Allow & Continue", onClick = onContinue, modifier = Modifier.fillMaxWidth())
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
            "Red Alert",
            "A confirmed high-risk threat was detected. Take immediate action — block the number, stop the transaction, and consider filing a cybercrime report."
        ),
        TourPage(
            Icons.Filled.Warning,
            SuspiciousAmber,
            "Yellow Alert",
            "Suspicious activity detected. The call, link, or file shows unusual patterns. Verify before proceeding and be cautious."
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
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
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
    var bankName by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundLight)) {
        RakshakSetuTopBar(title = "Bank & Payment Setup")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Optional Setup", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Add your bank details for faster cybercrime reporting. All stored locally only.", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)

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
            PrimaryButton("Save & Continue", onClick = onContinue, modifier = Modifier.weight(2f))
        }
    }
}
