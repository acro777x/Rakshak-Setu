package com.rakshaksetu.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakshaksetu.app.pipeline.ModelDownloadManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    // In a real app you'd use a ViewModel, but we use a simple state here for the guard
    var isSetupComplete by remember { mutableStateOf(ModelDownloadManager.areModelsReady(context)) }

    if (!isSetupComplete) {
        SetupScreen(onSetupComplete = { isSetupComplete = true })
    } else {
        // Models are ready, proceed to main dashboard
        DashboardScreen()
    }
}

@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var downloadState by remember { mutableStateOf<ModelDownloadManager.DownloadState>(ModelDownloadManager.DownloadState.Idle) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var currentFile by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            ModelDownloadManager.downloadAllModels(context).collect { state ->
                downloadState = state
                when (state) {
                    is ModelDownloadManager.DownloadState.Downloading -> {
                        currentProgress = state.progress
                        currentFile = state.fileName
                    }
                    is ModelDownloadManager.DownloadState.Success -> {
                        onSetupComplete()
                    }
                    else -> {}
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Rakshak Setu", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI Scam Interceptor", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Spacer(modifier = Modifier.height(64.dp))
        
        when (downloadState) {
            is ModelDownloadManager.DownloadState.Idle,
            is ModelDownloadManager.DownloadState.Downloading -> {
                CircularProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Downloading AI Models...", fontWeight = FontWeight.Medium)
                Text("File: $currentFile", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${(currentProgress * 100).toInt()}%", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            is ModelDownloadManager.DownloadState.Error -> {
                val errorMsg = (downloadState as ModelDownloadManager.DownloadState.Error).message
                Text("Setup Failed", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = {
                    coroutineScope.launch {
                        ModelDownloadManager.downloadAllModels(context).collect { state ->
                            downloadState = state
                            if (state is ModelDownloadManager.DownloadState.Downloading) {
                                currentProgress = state.progress
                                currentFile = state.fileName
                            }
                            if (state is ModelDownloadManager.DownloadState.Success) {
                                onSetupComplete()
                            }
                        }
                    }
                }) {
                    Text("Retry Download")
                }
            }
            is ModelDownloadManager.DownloadState.Success -> {
                Text("Setup Complete!", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        val whisperModel = java.io.File(context.filesDir, ModelDownloadManager.WHISPER_FILENAME).absolutePath
        val embeddingModel = java.io.File(context.filesDir, ModelDownloadManager.EMBEDDING_FILENAME).absolutePath
        
        com.rakshaksetu.app.pipeline.EmbeddingEngine.init(this, embeddingModel)
        val whisperEngine = com.rakshaksetu.app.pipeline.WhisperEngine(whisperModel)
        
        // This validates that the engine loads successfully from internal storage
        // A full Coordinator instance could be managed via ViewModel here
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Rakshak Setu Dashboard", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("AI Models loaded from internal storage.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Ready to intercept calls.", fontWeight = FontWeight.Medium)
        
        // TODO: Full Dashboard UI (Section 3) showing stats
    }
}
