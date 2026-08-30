package com.rakshaksetu.app.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rakshaksetu.app.BuildConfig
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.elder.ElderModeStore
import com.rakshaksetu.app.elder.RakshakAppTheme
import com.rakshaksetu.app.evidence.StatementGenerator
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.report.PortalFieldMapper
import com.rakshaksetu.app.report.ReportField
import com.rakshaksetu.app.report.UserProfileStore
import com.rakshaksetu.app.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GovtReportWebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
        const val EXTRA_PORTAL = "EXTRA_PORTAL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val portalName = intent.getStringExtra(EXTRA_PORTAL) ?: ""
        val initialPortal = if (portalName.contains("CHAKSHU", ignoreCase = true)) {
            PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI
        } else {
            PortalFieldMapper.Portal.NCRP_CYBERCRIME
        }

        val elderMode = ElderModeStore(applicationContext).isEnabled

        setContent {
            RakshakAppTheme(elderModeEnabled = elderMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = BackgroundLight) {
                    GovtReportScreen(
                        callId = callId,
                        initialPortal = initialPortal,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

private data class PortalStep(val title: String, val detail: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovtReportScreen(
    callId: String,
    initialPortal: PortalFieldMapper.Portal = PortalFieldMapper.Portal.NCRP_CYBERCRIME,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedPortal by remember { mutableStateOf(initialPortal) }
    var fillStats by remember { mutableStateOf(Triple(0, 0, 0)) }
    var showGuide by remember { mutableStateOf(false) }
    var webProgress by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    val profile = remember { UserProfileStore(context) }
    val detectionResult: DetectionResult? = remember(callId) {
        val last = DetectionStore.getLastResult(context)
        when {
            callId.isNotBlank() && last?.callId == callId -> last
            else -> last ?: if (BuildConfig.DEBUG) FakePipelineEmitter.digitalArrestResult() else null
        }
    }

    val epochMs = (detectionResult?.callEndEpoch ?: System.currentTimeMillis()).let {
        if (it > 100_000_000_000L) it else it * 1000L
    }
    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(epochMs))
    val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMs))
    val suspectPhone = detectionResult?.phoneNumber ?: ""

    val statementText = remember(detectionResult) {
        detectionResult?.let {
            try {
                StatementGenerator.getEvidenceStatement(context, it.callId)
                    ?: StatementGenerator.generate(it)
            } catch (e: Exception) {
                "Complaint regarding suspected fraud call from ${it.phoneNumber} on $dateStr at $timeStr."
            }
        }.orEmpty()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Government Reporting Copilot",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RakshakSetuBlue
                            )
                            Text(
                                text = if (selectedPortal == PortalFieldMapper.Portal.NCRP_CYBERCRIME) "Official NCRP Portal (MHA)" else "Official Chakshu Portal (DoT)",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RakshakSetuBlue)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            webViewInstance?.loadUrl(selectedPortal.url)
                            scope.launch { snackbarHostState.showSnackbar("Reloading portal...") }
                        }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload Portal", tint = RakshakSetuBlue)
                        }
                        IconButton(onClick = { showGuide = true }) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = "Filing Guide", tint = RakshakSetuBlue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = SurfaceWhite,
                        titleContentColor = TextPrimary
                    )
                )

                if (isLoading && webProgress < 100) {
                    LinearProgressIndicator(
                        progress = { webProgress / 100f },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = RakshakSetuBlue,
                        trackColor = RakshakSetuBlueLight.copy(alpha = 0.3f)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = SurfaceWhite,
                shadowElevation = 12.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                border = BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Header Status & Diagnostics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (fillStats.second > 0) SafeGreen else SuspiciousAmber)
                            )
                            Text(
                                text = if (fillStats.second > 0) "${fillStats.second} Fields Auto-Populated ⚡" else "AI Copilot Active",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (fillStats.second > 0) SafeGreen else RakshakSetuBlue
                            )
                        }

                        if (suspectPhone.isNotBlank()) {
                            Surface(
                                color = BlockedRedLight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Suspect: $suspectPhone",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BlockedRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Quick-Copy Horizontal Carousel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(suspectPhone))
                                scope.launch { snackbarHostState.showSnackbar("Copied Suspect Phone: $suspectPhone") }
                            },
                            label = { Text("📞 $suspectPhone", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                        )
                        SuggestionChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(statementText))
                                scope.launch { snackbarHostState.showSnackbar("Copied Police Evidence Statement") }
                            },
                            label = { Text("📝 Legal Statement", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold) }
                        )
                        SuggestionChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(dateStr))
                                scope.launch { snackbarHostState.showSnackbar("Copied Date: $dateStr") }
                            },
                            label = { Text("📅 $dateStr", style = MaterialTheme.typography.labelSmall) }
                        )
                        SuggestionChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(timeStr))
                                scope.launch { snackbarHostState.showSnackbar("Copied Time: $timeStr") }
                            },
                            label = { Text("⏰ $timeStr", style = MaterialTheme.typography.labelSmall) }
                        )
                        if (profile.fullName.isNotBlank()) {
                            SuggestionChip(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(profile.fullName))
                                    scope.launch { snackbarHostState.showSnackbar("Copied Name: ${profile.fullName}") }
                                },
                                label = { Text("👤 ${profile.fullName}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (profile.phone.isNotBlank()) {
                            SuggestionChip(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(profile.phone))
                                    scope.launch { snackbarHostState.showSnackbar("Copied Complainant Phone") }
                                },
                                label = { Text("📱 ${profile.phone}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Main Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val r = detectionResult
                                if (r == null) {
                                    scope.launch { snackbarHostState.showSnackbar("No incident loaded to auto-fill") }
                                } else {
                                    webViewInstance?.let { injectAutoFill(it, selectedPortal, profile, r) }
                                    scope.launch { snackbarHostState.showSnackbar("⚡ Injected complainant & threat evidence into portal!") }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RakshakSetuBlue),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.5f).height(50.dp)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, tint = SurfaceWhite, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("⚡ AUTO-FILL FORM", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = SurfaceWhite)
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(statementText))
                                scope.launch { snackbarHostState.showSnackbar("Copied complete incident statement!") }
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.5.dp, RakshakSetuBlue),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = RakshakSetuBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Statement", color = RakshakSetuBlue, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }

                    Text(
                        text = "🔒 Security Note: Rakshak Setu never captures or stores OTPs, passwords, or CAPTCHAs.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        },
        containerColor = BackgroundLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Elegant Navigation Tabs
            TabRow(
                selectedTabIndex = if (selectedPortal == PortalFieldMapper.Portal.NCRP_CYBERCRIME) 0 else 1,
                containerColor = SurfaceWhite,
                contentColor = RakshakSetuBlue,
                divider = { HorizontalDivider(color = BorderColor) }
            ) {
                Tab(
                    selected = selectedPortal == PortalFieldMapper.Portal.NCRP_CYBERCRIME,
                    onClick = {
                        selectedPortal = PortalFieldMapper.Portal.NCRP_CYBERCRIME
                        fillStats = Triple(0, 0, 0)
                        webViewInstance?.loadUrl(selectedPortal.url)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Gavel, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("NCRP (Cybercrime)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
                Tab(
                    selected = selectedPortal == PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI,
                    onClick = {
                        selectedPortal = PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI
                        fillStats = Triple(0, 0, 0)
                        webViewInstance?.loadUrl(selectedPortal.url)
                    },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.RemoveRedEye, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Chakshu (Telecom)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                )
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.safeBrowsingEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        settings.setSupportMultipleWindows(false)
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                webProgress = newProgress
                                isLoading = newProgress < 100
                            }
                        }

                        addJavascriptInterface(
                            FillBridge { scanned, filled, blocked ->
                                fillStats = Triple(scanned, filled, blocked)
                            },
                            "AndroidBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val url = request.url?.toString() ?: return false
                                val host = request.url?.host?.lowercase() ?: ""
                                if (host.contains("cybercrime.gov.in") || host.contains("sancharsaathi.gov.in") || host.contains("nic.in") || host.contains("gov.in")) {
                                    return false // Stay inside WebView
                                }
                                try {
                                    if (url.startsWith("tel:") || url.startsWith("mailto:")) {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        view.context.startActivity(intent)
                                        return true
                                    }
                                } catch (ignored: Exception) {}
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                isLoading = false
                                val r = detectionResult ?: return
                                injectAutoFill(view, selectedPortal, profile, r)
                            }
                        }
                        loadUrl(selectedPortal.url)
                    }.also { webViewInstance = it }
                },
                update = { webView ->
                    webViewInstance = webView
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showGuide) {
        PortalGuideSheet(selectedPortal, onDismiss = { showGuide = false })
    }
}

@Composable
private fun PortalGuideSheet(portal: PortalFieldMapper.Portal, onDismiss: () -> Unit) {
    val steps: List<PortalStep> = when (portal) {
        PortalFieldMapper.Portal.NCRP_CYBERCRIME -> listOf(
            PortalStep("1. Citizen Login / Register", "Click 'Click Here for New User' or enter your Login ID / Mobile. Enter State, Login ID (email), Mobile Number → Get OTP → Enter CAPTCHA."),
            PortalStep("2. Auto-Fill Complainant Details", "Tap '⚡ AUTO-FILL FORM' to populate your name, email, phone, and state into the portal."),
            PortalStep("3. Select Cyber Crime Category", "For AI voice clone/fraud call: Choose 'Online Financial Fraud' → 'Cheating by Impersonation' or 'Digital Arrest'."),
            PortalStep("4. Incident & Suspect Data", "Rakshak Setu automatically loads the suspect's phone number, call timestamp, and legal FIR-ready incident statement."),
            PortalStep("5. Review & Submit", "Review the auto-populated complaint → Enter CAPTCHA → Submit. Save the 14-digit NCRP acknowledgment number.")
        )
        PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI -> listOf(
            PortalStep("1. Select Communication Medium", "Select 'CALL' as the communication medium."),
            PortalStep("2. Select Fraud Category", "Choose 'Financial Fraud' / 'Impersonation' / 'Digital Arrest'."),
            PortalStep("3. Auto-Fill with 1-Tap", "Tap '⚡ AUTO-FILL FORM' to instantly populate the suspect phone number, incident date, and time."),
            PortalStep("4. Submit Report", "Press Submit to block & flag the scam caller across Indian telecom networks.")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = RakshakSetuBlue)) {
                Text("Understood", color = SurfaceWhite, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = RakshakSetuBlue)
                Text("${portal.shortName} — Filing Guide", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                steps.forEach { s ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = BackgroundLight),
                        border = BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(s.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                            Text(s.detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
            }
        }
    )
}

class FillBridge(private val onStats: (Int, Int, Int) -> Unit) {
    @JavascriptInterface
    fun reportStats(scanned: Int, filled: Int, blocked: Int) {
        onStats(scanned, filled, blocked)
    }
}

private fun injectAutoFill(
    webView: WebView,
    portal: PortalFieldMapper.Portal,
    profile: UserProfileStore,
    result: DetectionResult
) {
    val complainantName = profile.fullName.ifBlank { "Citizen Complainant" }
    val complainantPhone = profile.phone.ifBlank { "9876543210" }
    val complainantEmail = profile.email.ifBlank { "citizen.complaint@gmail.com" }
    val complainantState = profile.state.ifBlank { "Delhi" }

    val payload = JSONObject().apply {
        put(ReportField.COMPLAINANT_NAME.name, complainantName)
        put(ReportField.MOBILE.name, complainantPhone)
        put(ReportField.ALT_MOBILE.name, profile.alternatePhone)
        put(ReportField.EMAIL.name, complainantEmail)
        put(ReportField.STATE.name, complainantState)
        put(ReportField.CITY.name, profile.city)
        put(ReportField.ADDRESS.name, profile.address)
        put(ReportField.AGE.name, profile.ageDeclared)
        put(ReportField.SUSPECT_PHONE.name, result.phoneNumber)

        val epochMs = if (result.callEndEpoch > 100_000_000_000L) result.callEndEpoch else result.callEndEpoch * 1000L
        put(ReportField.INCIDENT_DATE.name, SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(epochMs)))
        put(ReportField.INCIDENT_TIME.name, SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMs)))

        val statement = StatementGenerator.generate(result)
        put(ReportField.INCIDENT_DESCRIPTION.name, statement)
        put(ReportField.FRAUD_AMOUNT.name, "0")
    }

    val js = """
        (function() {
            var data = ${payload.toString()};
            var scanned = 0, filled = 0, blocked = 0;

            // 1. NCRP: Automatically navigate to "Click Here for New User" if currently on login screen
            var allLinks = document.querySelectorAll('a, button, input[type="button"], input[type="submit"]');
            for (var i = 0; i < allLinks.length; i++) {
                var elText = (allLinks[i].innerText || allLinks[i].textContent || allLinks[i].value || '').trim().toLowerCase();
                if (elText.includes('click here for new user') || (elText.includes('new user') && !elText.includes('existing'))) {
                    allLinks[i].click();
                    setTimeout(function() {
                        if (window.AndroidBridge && window.AndroidBridge.reportStats) {
                            window.AndroidBridge.reportStats(1, 1, 0);
                        }
                    }, 500);
                    return;
                }
            }

            // 2. Scan and fill all form fields on the active page (Registration, Login, or Incident Report)
            var inputs = document.querySelectorAll('input, textarea, select');
            scanned = inputs.length;

            inputs.forEach(function(el) {
                var name = (el.name || '').toLowerCase();
                var id = (el.id || '').toLowerCase();
                var placeholder = (el.placeholder || '').toLowerCase();
                var tag = el.tagName.toLowerCase();

                // Respect security boundaries: NEVER touch captcha, OTP, or password
                if (name.includes('captcha') || id.includes('captcha') || name.includes('otp') || id.includes('otp') || el.type === 'password') {
                    blocked++;
                    return;
                }

                // Dropdown selectors (State, Category, Communication Medium)
                if (tag === 'select') {
                    if (name.includes('state') || id.includes('state') || id.includes('ddlstate')) {
                        if (data.STATE) {
                            var matched = false;
                            for (var i = 0; i < el.options.length; i++) {
                                if (el.options[i].text.toLowerCase().includes(data.STATE.toLowerCase())) {
                                    el.selectedIndex = i;
                                    el.dispatchEvent(new Event('change', { bubbles: true }));
                                    filled++;
                                    matched = true;
                                    break;
                                }
                            }
                            if (!matched && el.options.length > 1) {
                                el.selectedIndex = 1;
                                el.dispatchEvent(new Event('change', { bubbles: true }));
                                filled++;
                            }
                        }
                    } else if (name.includes('medium') || id.includes('medium')) {
                        // Chakshu Medium dropdown -> Call
                        for (var i = 0; i < el.options.length; i++) {
                            if (el.options[i].text.toLowerCase().includes('call') || el.options[i].value.toLowerCase().includes('call')) {
                                el.selectedIndex = i;
                                el.dispatchEvent(new Event('change', { bubbles: true }));
                                filled++;
                                break;
                            }
                        }
                    }
                    return;
                }

                // Text inputs (Mobile, Name, Email/LoginID, Suspect, Description, Date, Time)
                if (name.includes('mobile') || id.includes('mobile') || placeholder.includes('mobile') || id.includes('txtmobile')) {
                    if (data.MOBILE && (!el.value || el.value === '+91')) {
                        el.value = data.MOBILE;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        filled++;
                    }
                } else if (name.includes('name') || id.includes('name') || placeholder.includes('name') || id.includes('txtname')) {
                    if (data.COMPLAINANT_NAME && !el.value) {
                        el.value = data.COMPLAINANT_NAME;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        filled++;
                    }
                } else if (name.includes('email') || id.includes('email') || placeholder.includes('email') || id.includes('txtemail') || id.includes('login') || id.includes('txtloginid')) {
                    if (data.EMAIL && !el.value) {
                        el.value = data.EMAIL;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        filled++;
                    }
                } else if (name.includes('suspect') || id.includes('suspect') || placeholder.includes('suspect') || id.includes('txtsuspect')) {
                    if (data.SUSPECT_PHONE && !el.value) {
                        el.value = data.SUSPECT_PHONE;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        filled++;
                    }
                } else if (name.includes('desc') || id.includes('desc') || placeholder.includes('desc') || tag === 'textarea' || id.includes('txtremarks')) {
                    if (data.INCIDENT_DESCRIPTION && !el.value) {
                        el.value = data.INCIDENT_DESCRIPTION;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        filled++;
                    }
                } else if (name.includes('date') || id.includes('date') || id.includes('txtdate')) {
                    if (data.INCIDENT_DATE && !el.value) {
                        el.value = data.INCIDENT_DATE;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        filled++;
                    }
                } else if (name.includes('time') || id.includes('time') || id.includes('txttime')) {
                    if (data.INCIDENT_TIME && !el.value) {
                        el.value = data.INCIDENT_TIME;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        filled++;
                    }
                }
            });

            if (window.AndroidBridge && window.AndroidBridge.reportStats) {
                window.AndroidBridge.reportStats(scanned, filled, blocked);
            }
        })();
    """.trimIndent()

    webView.evaluateJavascript(js, null)
}

fun buildFillerScript(payloadJson: String): String {
    return """
        (function() {
            var isProtected = function(el) {
                var txt = (el.name || '') + ' ' + (el.id || '') + ' ' + (el.placeholder || '');
                return txt.toLowerCase().indexOf('captcha') !== -1 || txt.toLowerCase().indexOf('otp') !== -1;
            };
            var label = 'field';
            if (window.AndroidBridge && window.AndroidBridge.onFillResults) {
                window.AndroidBridge.onFillResults();
            }
        })();
    """.trimIndent()
}
