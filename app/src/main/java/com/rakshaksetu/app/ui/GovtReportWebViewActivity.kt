package com.rakshaksetu.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.rakshaksetu.app.BuildConfig
import com.rakshaksetu.app.consent.ConsentStore
import com.rakshaksetu.app.debug.FakePipelineEmitter
import com.rakshaksetu.app.elder.ElderModeStore
import com.rakshaksetu.app.elder.RakshakAppTheme
import com.rakshaksetu.app.evidence.StatementGenerator
import com.rakshaksetu.app.model.DetectionResult
import com.rakshaksetu.app.model.DetectionStore
import com.rakshaksetu.app.report.PortalFieldMapper
import com.rakshaksetu.app.report.ReportField
import com.rakshaksetu.app.report.UserProfileStore
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GovtReportWebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALL_ID = "EXTRA_CALL_ID"
    }

    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra(EXTRA_CALL_ID) ?: ""
        val elderMode = ElderModeStore(applicationContext).isEnabled

        setContent {
            RakshakAppTheme(elderModeEnabled = elderMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GovtReportScreen(
                        callId = callId,
                        onBack = { finish() },
                        onWebViewCreated = { webViewRef = it },
                        onLoadUrl = { url -> webViewRef?.loadUrl(url) },
                        onInjectNow = { portal, profile, result ->
                            webViewRef?.let { injectAutoFill(it, portal, profile, result) }
                        }
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onDestroy() {
        webViewRef?.apply {
            loadUrl("about:blank")
            (parent as? ViewGroup)?.removeAllViews()
            destroy()
        }
        webViewRef = null
        super.onDestroy()
    }
}

private data class PortalStep(val title: String, val detail: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GovtReportScreen(
    callId: String,
    onBack: () -> Unit,
    onWebViewCreated: (WebView?) -> Unit,
    onLoadUrl: (String) -> Unit,
    onInjectNow: (PortalFieldMapper.Portal, UserProfileStore, DetectionResult) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedPortal by remember { mutableStateOf(PortalFieldMapper.Portal.NCRP_CYBERCRIME) }
    var fillStats by remember { mutableStateOf(Triple(0, 0, 0)) }
    var manualMode by remember { mutableStateOf(false) }
    var showGuide by remember { mutableStateOf(false) }

    val consentStore = remember { ConsentStore(context) }
    if (!consentStore.isShieldActive) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Shield, contentDescription = null)
            Spacer(Modifier.height(12.dp))
            Text("Shield is paused", fontWeight = FontWeight.Bold)
            Text(
                "Enable the shield to use guided reporting.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val profile = remember { UserProfileStore(context) }
    val detectionResult: DetectionResult? = remember(callId) {
        val last = DetectionStore.getLastResult(context)
        when {
            callId.isNotBlank() && last?.callId == callId -> last
            else -> last ?: if (BuildConfig.DEBUG) FakePipelineEmitter.digitalArrestResult() else null
        }
    }

    val epochMs = (detectionResult?.callEndEpoch ?: System.currentTimeMillis() / 1000).let {
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
            TopAppBar(
                title = { Text("Government Reporting", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showGuide = true }) {
                        Icon(Icons.Default.MenuBook, contentDescription = "Field guide")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Quick 1-Tap Copy Chips Toolbar
                    Text(
                        "Quick Copy for Manual Paste:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SuggestionChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(suspectPhone))
                                scope.launch { snackbarHostState.showSnackbar("Copied Suspect Phone: $suspectPhone") }
                            },
                            label = { Text("📞 Suspect Phone", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(statementText))
                                scope.launch { snackbarHostState.showSnackbar("Copied Legal Statement") }
                            },
                            label = { Text("📝 Statement", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(dateStr))
                                scope.launch { snackbarHostState.showSnackbar("Copied Date: $dateStr") }
                            },
                            label = { Text("📅 $dateStr", fontSize = 11.sp) }
                        )
                        SuggestionChip(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(timeStr))
                                scope.launch { snackbarHostState.showSnackbar("Copied Time: $timeStr") }
                            },
                            label = { Text("⏰ $timeStr", fontSize = 11.sp) }
                        )
                        if (profile.fullName.isNotBlank()) {
                            SuggestionChip(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(profile.fullName))
                                    scope.launch { snackbarHostState.showSnackbar("Copied Name: ${profile.fullName}") }
                                },
                                label = { Text("👤 Name", fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Fill Button & Diagnostics
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val r = detectionResult
                                if (r == null) {
                                    scope.launch { snackbarHostState.showSnackbar("No incident dossier loaded") }
                                } else {
                                    onInjectNow(selectedPortal, profile, r)
                                    scope.launch { snackbarHostState.showSnackbar("⚡ Auto-filling complaint form...") }
                                }
                            },
                            enabled = detectionResult != null,
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("FILL THIS FORM", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(statementText))
                                scope.launch { snackbarHostState.showSnackbar("Full incident statement copied!") }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Statement", maxLines = 1)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            fillStats.first == 0 -> "Navigate to complaint form and click 'FILL THIS FORM' (CAPTCHA & OTP entered by you)"
                            else -> "Scanned ${fillStats.first} inputs • Filled ${fillStats.second} • Skipped ${fillStats.third} protected"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = if (selectedPortal == PortalFieldMapper.Portal.NCRP_CYBERCRIME) 0 else 1) {
                Tab(
                    selected = selectedPortal == PortalFieldMapper.Portal.NCRP_CYBERCRIME,
                    onClick = {
                        selectedPortal = PortalFieldMapper.Portal.NCRP_CYBERCRIME
                        fillStats = Triple(0, 0, 0)
                        onLoadUrl(selectedPortal.url)
                    },
                    text = { Text("NCRP (cybercrime.gov.in)", maxLines = 1) }
                )
                Tab(
                    selected = selectedPortal == PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI,
                    onClick = {
                        selectedPortal = PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI
                        fillStats = Triple(0, 0, 0)
                        onLoadUrl(selectedPortal.url)
                    },
                    text = { Text("Chakshu (Telecom Fraud)", maxLines = 1) }
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
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

                        addJavascriptInterface(
                            FillBridge { scanned, filled, blocked ->
                                fillStats = Triple(scanned, filled, blocked)
                            },
                            "AndroidBridge"
                        )

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val host = request.url?.host?.lowercase()
                                if (host.isNullOrBlank()) return false
                                return false // Allow in-portal navigation without blocking
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                val r = detectionResult ?: return
                                if (!manualMode) {
                                    injectAutoFill(view, selectedPortal, profile, r)
                                }
                            }
                        }
                    }.also { onWebViewCreated(it) }
                },
                update = {},
                modifier = Modifier.fillMaxSize()
            )

            LaunchedEffect(Unit) {
                onLoadUrl(selectedPortal.url)
            }
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
            PortalStep("1. Choose complaint type", "Money LOST → 'Financial Fraud'. Threats / Impersonation → 'Other Cybercrime'."),
            PortalStep("2. Citizen Login (YOU)", "Enter YOUR mobile number, type OTP & CAPTCHA yourself (Rakshak Setu never accesses credentials)."),
            PortalStep("3. Category selection", "Pick: Digital Arrest / Impersonation / Fake CBI-Police / KYC Expiry."),
            PortalStep("4. Complainant details", "Click 'FILL THIS FORM' or use quick-copy chips."),
            PortalStep("5. Incident details", "Suspect mobile number, date and time auto-filled from call dossier."),
            PortalStep("6. Complaint description", "Auto-fills standard legal complaint text mentioning IT Act & BNS provisions."),
            PortalStep("7. Submit", "Enter CAPTCHA and submit. Save the acknowledgment number.")
        )
        PortalFieldMapper.Portal.CHAKSHU_SANCHARSAATHI -> listOf(
            PortalStep("1. Login (YOU)", "Mobile number + OTP entered by you."),
            PortalStep("2. Report communication", "Choose 'Report suspected fraud communication' → CALL."),
            PortalStep("3. Category", "Select Financial Fraud → Digital Arrest / KYC / Courier."),
            PortalStep("4. Auto-fill", "Press 'FILL THIS FORM' to populate suspect phone, date and time."),
            PortalStep("5. Submit", "Press Submit. Chakshu flags the scam number across Indian telecom networks.")
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss) { Text("Got it") } },
        title = { Text("${portal.displayName} — Guide", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                steps.forEach { s ->
                    Column {
                        Text(s.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(s.detail, fontSize = 12.5.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    )
}

internal fun parseFillReport(json: String): Triple<Int, Int, Int> =
    try {
        val obj = JSONObject(json)
        Triple(
            obj.optInt("scanned", 0),
            obj.optInt("filled", 0),
            obj.optInt("blocked", 0)
        )
    } catch (e: Exception) {
        Triple(0, 0, 0)
    }

internal fun injectAutoFill(
    view: WebView,
    portal: PortalFieldMapper.Portal,
    profile: UserProfileStore,
    result: DetectionResult
) {
    val epochMs = if (result.callEndEpoch > 100_000_000_000L) result.callEndEpoch else result.callEndEpoch * 1000L
    val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(epochMs))
    val timeStr = SimpleDateFormat("HH:mm", Locale.US).format(Date(epochMs))

    val description = buildString {
        append("I received a suspected fraud call from ${result.phoneNumber} on $dateStr at $timeStr ")
        append("for approximately ${result.durationSec} seconds. ")
        if (!result.scamType.isNullOrBlank()) {
            append("The caller used a ${result.scamType!!.replace('_', ' ')} script. ")
        }
        append("Key statements captured by on-device analysis: ${result.flaggedSegments.joinToString("; ") { it.text }}. ")
        append("I am filing this complaint under IT Act 2000 and BNS 2023 provisions.")
    }

    val values: Map<ReportField, String> = mapOf(
        ReportField.COMPLAINANT_NAME to profile.fullName,
        ReportField.MOBILE to profile.phone,
        ReportField.ALT_MOBILE to profile.alternatePhone,
        ReportField.EMAIL to profile.email,
        ReportField.STATE to profile.state,
        ReportField.CITY to profile.city,
        ReportField.ADDRESS to profile.address,
        ReportField.AGE to profile.ageDeclared,
        ReportField.INCIDENT_DESCRIPTION to description,
        ReportField.INCIDENT_DATE to dateStr,
        ReportField.INCIDENT_TIME to timeStr,
        ReportField.SUSPECT_PHONE to result.phoneNumber.filter { it.isDigit() || it == '+' }
    )

    val payload = PortalFieldMapper.buildPayloadJson(portal, values)
    view.evaluateJavascript(buildFillerScript(payload), null)
}

internal fun buildFillerScript(payloadJson: String): String {
    return """
        (function(){
          var payload = $payloadJson;
          var BLOCK = ['captcha','capcha','captch','otp','one_time_password','onetimepassword','verification_code','verifycode','password','passwd','pwd','pin','passcode','aadhaar','aadhar','uidai','vid_number','cvv','card_number','cardnumber','account_number','accountnumber','acct','upi','ifsc','debit','credit_card'];
          var stats = {scanned:0, filled:0, blocked:0};

          function isProtected(el){
            try {
              var type=(el.getAttribute('type')||el.type||'').toLowerCase();
              if(type==='password'||type==='file'||type==='hidden'||el.readOnly) return true;
              var attrs=[el.id||'',el.name||'',el.placeholder||'',el.getAttribute('aria-label')||''].join(' ').toLowerCase();
              for(var i=0;i<BLOCK.length;i++){ if(attrs.indexOf(BLOCK[i])>=0) return true; }
              return false;
            } catch(e) { return true; }
          }

          function q(doc,sel){ try{ return doc.querySelector(sel); }catch(e){ return null; } }

          function assocText(el){
            try{
              var id=el.id;
              if(id){ var l=el.ownerDocument.querySelector('label[for="'+id.replace(/"/g,'\\\"')+'"]'); if(l&&l.textContent) return l.textContent.trim(); }
              var wrap=el.closest?el.closest('label'):null; if(wrap&&wrap.textContent) return wrap.textContent.trim();
              var td=el.closest?el.closest('td'):null;
              if(td){ var prev=td.previousElementSibling; while(prev&&!prev.textContent.trim()){prev=prev.previousElementSibling;} if(prev&&prev.textContent) return prev.textContent.trim(); }
              var row=el.closest?el.closest('tr'):null;
              if(row){ var th=row.querySelector('th'); if(th&&th.textContent) return th.textContent.trim(); }
            }catch(e){}
            return (el.getAttribute('aria-label')||el.placeholder||'');
          }

          function setValue(el,value){
            try{
              if(!el) return false;
              if(el.tagName==='SELECT'){
                var opts=el.options;
                for(var i=0;i<opts.length;i++){
                  if(opts[i].text.toLowerCase().indexOf(value.toLowerCase())>=0 || opts[i].value.toLowerCase().indexOf(value.toLowerCase())>=0){
                    el.selectedIndex=i;
                    el.dispatchEvent(new Event('change',{bubbles:true}));
                    return true;
                  }
                }
                return false;
              }
              el.focus();
              try {
                var proto = el.tagName==='TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                var desc = Object.getOwnPropertyDescriptor(proto,'value');
                if (desc && desc.set) {
                  desc.set.call(el, value);
                } else {
                  el.value = value;
                }
              } catch(pe) {
                el.value = value;
              }
              el.dispatchEvent(new Event('input',{bubbles:true}));
              el.dispatchEvent(new Event('change',{bubbles:true}));
              el.dispatchEvent(new Event('blur',{bubbles:true}));
              return true;
            }catch(e){ return false; }
          }

          function fillDoc(doc){
            if(!doc||!doc.querySelectorAll) return;
            var els = doc.querySelectorAll('input:not([type=submit]):not([type=button]):not([type=checkbox]):not([type=radio]), textarea, select');
            stats.scanned += els.length;
            var done={};
            for(var j=0;j<payload.length;j++){
              var item=payload[j]; if(done[item.field]) continue;
              var target=null, blocked=false;
              for(var k=0;k<item.selectors.length;k++){
                var cand=q(doc,item.selectors[k]);
                if(cand){ if(isProtected(cand)){blocked=true;break;} target=cand; break; }
              }
              if(!target && !blocked && item.labels && item.labels.length){
                for(var m=0;m<els.length;m++){
                  var el=els[m];
                  if(isProtected(el)) continue;
                  if(el.value && el.value.length>0) continue;
                  var txt=assocText(el).toLowerCase();
                  if(!txt) continue;
                  for(var n=0;n<item.labels.length;n++){
                    if(txt.indexOf(item.labels[n])>=0){ target=el; break; }
                  }
                  if(target) break;
                }
              }
              if(blocked){ stats.blocked++; continue; }
              if(target && setValue(target,item.value)){ stats.filled++; done[item.field]=true; }
            }
          }

          try{ fillDoc(document); }catch(e){}
          function walk(w){
            try{
              for(var i=0;i<w.frames.length;i++){
                try{ fillDoc(w.frames[i].document); walk(w.frames[i]); }catch(fe){}
              }
            }catch(e){}
          }
          walk(window);
          try{ AndroidBridge.onFillResults(JSON.stringify(stats)); }catch(be){}
        })();
    """.trimIndent()
}

private class FillBridge(private val callback: (Int, Int, Int) -> Unit) {
    @JavascriptInterface
    fun onFillResults(statsJson: String) {
        val parsed = parseFillReport(statsJson)
        callback(parsed.first, parsed.second, parsed.third)
    }
}
