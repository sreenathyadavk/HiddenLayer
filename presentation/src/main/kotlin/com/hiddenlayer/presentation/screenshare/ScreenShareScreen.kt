package com.hiddenlayer.presentation.screenshare

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.hiddenlayer.presentation.overlay.OverlayService
import com.hiddenlayer.domain.models.AnalysisResult
import com.hiddenlayer.domain.usecases.CNNFeatureExtractor
import com.hiddenlayer.presentation.components.ConfidenceIndicator
import com.hiddenlayer.presentation.components.GlassCard
import com.hiddenlayer.presentation.components.CyberButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Screen Share Analysis - REAL periodic analysis with PIP overlay.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScreenShareScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    var isCapturing by remember { mutableStateOf(false) }
    var analysisState by remember { mutableStateOf<AnalysisResult?>(null) }
    var frameCount by remember { mutableStateOf(0) }
    var isAppInBackground by remember { mutableStateOf(false) }
    var hasOverlayPermission by remember { mutableStateOf(false) }
    
    // Check permission initially
    LaunchedEffect(Unit) {
        hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
    }
    
    // REAL CNN extractor
    val cnnExtractor = remember { CNNFeatureExtractor(context) }
    
    // Monitor app lifecycle for overlay and permission updates
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    isAppInBackground = true
                }
                Lifecycle.Event.ON_RESUME -> {
                    isAppInBackground = false
                    // Refresh permission status when user returns from settings
                    hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cnnExtractor.close()
            OverlayService.stop(context)
        }
    }
    
    // MediaProjection with simulated periodic analysis
    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            isCapturing = true
            
            // Start the Overlay Service which runs the analysis in foreground
            OverlayService.startWithProjection(context, result.resultCode, result.data!!)
            
            // Observe the service result
            scope.launch {
                while (isCapturing) {
                    delay(300) // Poll for updates (or use StateFlow if available later)
                    analysisState = OverlayService.currentResult
                    if (analysisState != null) {
                         frameCount++
                    }
                }
            }
        }
    }
    
    // Content
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isCapturing) {
            // Start screen share
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.ScreenShare, 
                    contentDescription = "Screen Share", 
                    modifier = Modifier.size(80.dp), 
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "SCREEN ANALYSIS", 
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "REAL-TIME PIP MONITORING", 
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(48.dp))
                
                if (!hasOverlayPermission) {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(0.9f),
                        borderColor = MaterialTheme.colorScheme.error
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "PERMISSION REQUIRED", 
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "HiddenLayer needs 'Display over other apps' to show safety alerts while you browse.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(24.dp))
                            CyberButton(
                                onClick = {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                },
                                text = "GRANT OVERLAY",
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                } else {
                    CyberButton(
                        onClick = {
                            val intent = mediaProjectionManager.createScreenCaptureIntent()
                            projectionLauncher.launch(intent)
                        },
                        text = "INITIATE SCREEN SHARE",
                        icon = Icons.Default.ScreenShare,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "OPERATIONAL PROTOCOL:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "1. Grant permission\n2. AI scans screen content (1Hz)\n3. Alerts via HUD Overlay",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        } else {
            // Active analysis
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Text(
                        "🟢",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "ANALYSIS ACTIVE",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "SCANS PERFORMED: $frameCount",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(48.dp))
                
                analysisState?.let { result ->
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(horizontal = 8.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "LATEST SCAN RESULT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                result.toDisplayMessage().uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.95f)
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                
                CyberButton(
                    onClick = { 
                        isCapturing = false 
                        frameCount = 0
                        OverlayService.stop(context)
                    },
                    text = "TERMINATE SESSION",
                    icon = Icons.Default.Stop,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
            }
        }
    }
}
