package com.hiddenlayer.presentation.camera

import android.Manifest
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.hiddenlayer.presentation.components.ConfidenceIndicator
import com.hiddenlayer.domain.models.AnalysisResult
import com.hiddenlayer.domain.pipeline.FramePipeline
import com.hiddenlayer.presentation.components.ScannerAnimation
import com.hiddenlayer.presentation.components.GlassCard
import com.hiddenlayer.presentation.components.CyberButton
import com.hiddenlayer.presentation.components.StatusBadge

/**
 * Camera screen with REAL-TIME analysis overlay.
 * 
 * Connected to actual pipeline - analyses live camera feed.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)
    
    when {
        cameraPermission.status.isGranted -> {
            CameraPreviewWithAnalysis()
        }
        else -> {
            CameraPermissionRequest(onRequestPermission = { cameraPermission.launchPermissionRequest() })
        }
    }
}

@Composable
fun CameraPermissionRequest(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Camera access required",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "HiddenLayer needs camera access to analyze video in real-time",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun CameraPreviewWithAnalysis() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Create pipeline
    val pipeline = remember { FramePipeline(context, lifecycleOwner) }
    val viewModel = remember { CameraViewModel(pipeline) }
    
    // Collect analysis state
    val analysisState by viewModel.analysisState.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    
    // Camera selection state
    var selectedCamera by remember { mutableStateOf<CameraSelection?>(null) }
    
    // Create PreviewView ONCE and reuse it
    val previewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    
    // Only start analysis when camera is selected
    LaunchedEffect(selectedCamera) {
        val camera = selectedCamera
        if (camera != null) {
            // Stop previous if running
            viewModel.stopAnalysis()
            
            val selector = when (camera) {
                CameraSelection.FRONT -> androidx.camera.core.CameraSelector.DEFAULT_FRONT_CAMERA
                CameraSelection.BACK -> androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
            }
            
            viewModel.startAnalysis(previewView.surfaceProvider, selector)
        }
    }
    
    // Stop on disposal
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopAnalysis()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        when (selectedCamera) {
            null -> {
                // Camera selection screen
                CameraSelectionScreen(
                    onCameraSelected = { selection ->
                        selectedCamera = selection
                    }
                )
            }
            else -> {
                // Camera preview
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Cyberpunk Scanner Line
                ScannerAnimation(modifier = Modifier.fillMaxSize())
                
                // Show analysis if available
                analysisState?.let { analysis ->
                    // Analysis overlay
                    AnalysisOverlay(
                        result = analysis.result,
                        processingTimeMs = analysis.processingTimeMs,
                        framesAnalyzed = analysis.framesAnalyzed,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp) // Avoid status bar
                    )
                    
                    // Confidence indicator
                    ConfidenceIndicator(
                        result = analysis.result,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp)
                    )
                }
                
                // Loading indicator - simplified to avoid Compose animation API issues
                if (!isProcessing && analysisState == null) {
                    Text(
                        text = "INITIALIZING SENSORS...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                // Change camera button
                CyberButton(
                    onClick = { selectedCamera = null },
                    text = "SWITCH CAM",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .width(120.dp)
                )
            }
        }
    }
}

enum class CameraSelection {
    FRONT, BACK
}

@Composable
fun CameraSelectionScreen(onCameraSelected: (CameraSelection) -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Select Camera",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "Choose which camera to use for real-time analysis",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Front camera button
            Button(
                onClick = { onCameraSelected(CameraSelection.FRONT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("📱 Front Camera", style = MaterialTheme.typography.titleMedium)
            }
            
            // Back camera button
            Button(
                onClick = { onCameraSelected(CameraSelection.BACK) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("📷 Back Camera", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun AnalysisOverlay(
    result: AnalysisResult,
    processingTimeMs: Long,
    framesAnalyzed: Int,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        borderColor = if (result is AnalysisResult.Authentic) Color(0xFF00E676) else Color(0xFFFF1744)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REAL-TIME ANALYSIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "${1000/processingTimeMs} FPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = result.toDisplayMessage().uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            when (result) {
                is AnalysisResult.Authentic -> {
                    StatusBadge(text = "AUTHENTIC", color = Color(0xFF00E676))
                }
                is AnalysisResult.Suspicious -> {
                    StatusBadge(text = "SUSPICIOUS", color = Color(0xFFFF9800))
                }
                is AnalysisResult.LikelyDeepfake -> {
                    StatusBadge(text = "DEEPFAKE DETECTED", color = Color(0xFFFF1744))
                }
                is AnalysisResult.Inconclusive -> {
                    StatusBadge(text = "INCONCLUSIVE", color = Color.Gray)
                }
            }
            
            // Debug info
            Text(
                text = "FRAMES: $framesAnalyzed | LATENCY: ${processingTimeMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
